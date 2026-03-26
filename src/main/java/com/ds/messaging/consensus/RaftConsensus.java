package com.ds.messaging.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Raft consensus state machine with election and commit flow.
 */
public class RaftConsensus {
    private static final long HEARTBEAT_INTERVAL_MS = 50L;
    private static final long MIN_ELECTION_TIMEOUT_MS = 150L;
    private static final long MAX_ELECTION_TIMEOUT_MS = 300L;

    private final String nodeId;
    private final List<String> peerNodeIds;
    private final Random random = new Random();

    private final AtomicReference<RaftState> state = new AtomicReference<>(RaftState.FOLLOWER);
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private final AtomicInteger commitIndex = new AtomicInteger(-1);

    private final List<LogEntry> logEntries = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, Set<String>> replicationAcksByIndex = new HashMap<>();
    private final Map<String, Integer> messageIndexById = new HashMap<>();

    private volatile String currentLeader;
    private volatile String votedFor;
    private volatile long lastHeartbeatMs;
    private volatile long electionStartedAtMs;
    private volatile long electionTimeoutMs;
    private volatile long lastLeaderTickMs;
    private final Set<String> votesGranted = new HashSet<>();

    public RaftConsensus(String nodeId, List<String> peerNodeIds) {
        this.nodeId = nodeId;
        this.peerNodeIds = peerNodeIds == null ? Collections.emptyList() : new ArrayList<>(peerNodeIds);
        this.lastHeartbeatMs = System.currentTimeMillis();
        this.electionStartedAtMs = 0L;
        this.electionTimeoutMs = nextElectionTimeoutMs();
        this.lastLeaderTickMs = 0L;
    }

    public synchronized void onFollowerTick() {
        if (state.get() != RaftState.FOLLOWER) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHeartbeatMs >= electionTimeoutMs) {
            startElection();
        }
    }

    public synchronized void onCandidateTick() {
        if (state.get() != RaftState.CANDIDATE) {
            return;
        }

        if (hasMajority(votesGranted.size())) {
            becomeLeader();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - electionStartedAtMs >= electionTimeoutMs) {
            startElection();
        }
    }

    public synchronized void onLeaderTick() {
        if (state.get() != RaftState.LEADER) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastLeaderTickMs == 0L || now - lastLeaderTickMs >= HEARTBEAT_INTERVAL_MS) {
            // Transport layer sends heartbeat RPCs; this maintains local leader cadence.
            currentLeader = nodeId;
            lastLeaderTickMs = now;
        }

        advanceCommitFromQuorumAcks();
    }

    public synchronized void handleAppendEntriesRPC(AppendEntriesRPC rpc) {
        if (rpc == null) {
            return;
        }

        if (rpc.getTerm() < currentTerm.get()) {
            return;
        }

        onHigherTermDiscovered(rpc.getTerm());
        if (rpc.getTerm() >= currentTerm.get()) {
            becomeFollower(rpc.getTerm());
            currentLeader = rpc.getLeaderId();
            lastHeartbeatMs = System.currentTimeMillis();
            resetElectionTimeout();

            if (rpc.getPrevLogIndex() >= 0 && !matchesLog(rpc.getPrevLogIndex(), rpc.getPrevLogTerm())) {
                return;
            }

            for (LogEntry entry : rpc.getEntries()) {
                mergeReplicatedEntry(entry);
            }

            if (rpc.getLeaderCommit() >= 0) {
                int boundedCommitIndex = Math.min(rpc.getLeaderCommit(), getLastLogIndex());
                commitUpTo(boundedCommitIndex);
            }
        }
    }

    public synchronized void handleRequestVoteRPC(RequestVoteRPC rpc) {
        if (rpc == null) {
            return;
        }

        if (rpc.getTerm() < currentTerm.get()) {
            return;
        }

        onHigherTermDiscovered(rpc.getTerm());

        if (rpc.getTerm() > currentTerm.get()) {
            becomeFollower(rpc.getTerm());
        }

        if (rpc.getTerm() == currentTerm.get() && (votedFor == null || votedFor.equals(rpc.getCandidateId()))) {
            votedFor = rpc.getCandidateId();
            lastHeartbeatMs = System.currentTimeMillis();
            resetElectionTimeout();
        }
    }

    public synchronized void startElection() {
        currentTerm.incrementAndGet();
        state.set(RaftState.CANDIDATE);
        currentLeader = null;
        votedFor = nodeId;
        votesGranted.clear();
        votesGranted.add(nodeId);
        electionStartedAtMs = System.currentTimeMillis();
        resetElectionTimeout();

        // Outbound RequestVote transport is implemented by the integration layer.
        onCandidateTick();
    }

    public synchronized void registerVote(String voterNodeId, int term, boolean granted) {
        if (!granted || voterNodeId == null) {
            return;
        }

        if (term > currentTerm.get()) {
            becomeFollower(term);
            return;
        }

        if (state.get() != RaftState.CANDIDATE || term != currentTerm.get()) {
            return;
        }

        votesGranted.add(voterNodeId);
        if (hasMajority(votesGranted.size())) {
            becomeLeader();
        }
    }

    public synchronized void triggerElectionIfLeaderFailed(String failedLeaderId) {
        if (failedLeaderId == null) {
            return;
        }

        if (failedLeaderId.equals(currentLeader) || (currentLeader == null && state.get() == RaftState.FOLLOWER)) {
            startElection();
        }
    }

    public synchronized void becomeLeader() {
        state.set(RaftState.LEADER);
        currentLeader = nodeId;
        lastLeaderTickMs = 0L;
        replicationAcksByIndex.clear();
        for (LogEntry entry : logEntries) {
            Set<String> ackSet = replicationAcksByIndex.computeIfAbsent(entry.getIndex(), key -> new HashSet<>());
            ackSet.add(nodeId);
        }
    }

    public synchronized void becomeFollower(int term) {
        if (term > currentTerm.get()) {
            currentTerm.set(term);
        }
        state.set(RaftState.FOLLOWER);
        votedFor = null;
        votesGranted.clear();
        lastHeartbeatMs = System.currentTimeMillis();
        resetElectionTimeout();
        replicationAcksByIndex.clear();
    }

    public synchronized void appendEntry(LogEntry entry) {
        if (entry == null) {
            return;
        }

        mergeReplicatedEntry(entry);

        if (state.get() == RaftState.LEADER) {
            Set<String> ackSet = replicationAcksByIndex.computeIfAbsent(entry.getIndex(), key -> new HashSet<>());
            ackSet.add(nodeId);
        }
    }

    public synchronized void commitEntry(LogEntry entry) {
        if (entry == null) {
            return;
        }
        commitUpTo(entry.getIndex());
    }

    public synchronized void registerAppendAck(String followerNodeId, int term, int logIndex, boolean success) {
        if (!success || followerNodeId == null) {
            return;
        }

        if (term > currentTerm.get()) {
            becomeFollower(term);
            return;
        }

        if (state.get() != RaftState.LEADER || term != currentTerm.get()) {
            return;
        }

        Set<String> ackSet = replicationAcksByIndex.computeIfAbsent(logIndex, key -> new HashSet<>());
        ackSet.add(followerNodeId);
        ackSet.add(nodeId);

        if (hasMajority(ackSet.size())) {
            commitUpTo(logIndex);
        }
    }

    public synchronized boolean commitReplicatedMessage(String messageId) {
        if (messageId == null) {
            return false;
        }

        Integer index = messageIndexById.get(messageId);
        if (index == null) {
            return false;
        }

        Set<String> ackSet = replicationAcksByIndex.computeIfAbsent(index, key -> new HashSet<>());
        ackSet.add(nodeId);

        if (hasMajority(ackSet.size())) {
            commitUpTo(index);
            return true;
        }
        return false;
    }

    public synchronized void onHigherTermDiscovered(int newTerm) {
        if (newTerm > currentTerm.get()) {
            becomeFollower(newTerm);
        }
    }

    public int getMajoritySize() {
        return clusterSize() / 2 + 1;
    }

    public synchronized int getVotesGrantedCount() {
        return votesGranted.size();
    }

    public synchronized boolean hasElectionTimedOut() {
        if (state.get() != RaftState.CANDIDATE) {
            return false;
        }
        return System.currentTimeMillis() - electionStartedAtMs >= electionTimeoutMs;
    }

    public long getElectionTimeoutMs() {
        return electionTimeoutMs;
    }

    public int getCurrentTerm() {
        return currentTerm.get();
    }

    public String getCurrentLeader() {
        return currentLeader;
    }

    public int getCommitIndex() {
        return commitIndex.get();
    }

    public RaftState getState() {
        return state.get();
    }

    public String getNodeId() {
        return nodeId;
    }

    public List<String> getPeerNodeIds() {
        return Collections.unmodifiableList(peerNodeIds);
    }

    public List<LogEntry> getLogEntriesSnapshot() {
        synchronized (logEntries) {
            return new ArrayList<>(logEntries);
        }
    }

    public synchronized List<LogEntry> getCommittedEntriesSnapshot() {
        List<LogEntry> committed = new ArrayList<>();
        for (LogEntry entry : logEntries) {
            if (entry.isCommitted()) {
                committed.add(entry);
            }
        }
        return committed;
    }

    public synchronized int getLastLogIndex() {
        if (logEntries.isEmpty()) {
            return -1;
        }
        return logEntries.get(logEntries.size() - 1).getIndex();
    }

    public synchronized int getLastLogTerm() {
        if (logEntries.isEmpty()) {
            return -1;
        }
        return logEntries.get(logEntries.size() - 1).getTerm();
    }

    public synchronized LogEntry getEntryByIndex(int index) {
        int position = positionOfIndex(index);
        if (position < 0) {
            return null;
        }
        return logEntries.get(position);
    }

    private boolean hasMajority(int votes) {
        return votes >= getMajoritySize();
    }

    private int clusterSize() {
        return peerNodeIds.size() + 1;
    }

    private void resetElectionTimeout() {
        electionTimeoutMs = nextElectionTimeoutMs();
    }

    private long nextElectionTimeoutMs() {
        long range = MAX_ELECTION_TIMEOUT_MS - MIN_ELECTION_TIMEOUT_MS + 1L;
        return MIN_ELECTION_TIMEOUT_MS + random.nextInt((int) range);
    }

    private void mergeReplicatedEntry(LogEntry entry) {
        int position = positionOfIndex(entry.getIndex());
        if (position >= 0) {
            LogEntry existing = logEntries.get(position);
            if (existing.getTerm() != entry.getTerm()) {
                truncateFromPosition(position);
                logEntries.add(entry);
            }
        } else {
            logEntries.add(entry);
        }

        if (entry.getData() != null && entry.getData().getMessageId() != null) {
            messageIndexById.put(entry.getData().getMessageId(), entry.getIndex());
        }
    }

    private void commitUpTo(int targetIndex) {
        if (targetIndex < 0) {
            return;
        }

        for (LogEntry entry : logEntries) {
            if (entry.getIndex() <= targetIndex) {
                entry.markCommitted();
            }
        }
        commitIndex.updateAndGet(existing -> Math.max(existing, targetIndex));
    }

    private void advanceCommitFromQuorumAcks() {
        int currentCommit = commitIndex.get();
        int lastIndex = getLastLogIndex();

        for (int idx = currentCommit + 1; idx <= lastIndex; idx++) {
            Set<String> ackSet = replicationAcksByIndex.get(idx);
            if (ackSet == null || !hasMajority(ackSet.size())) {
                break;
            }
            commitUpTo(idx);
        }
    }

    private boolean matchesLog(int index, int term) {
        LogEntry entry = getEntryByIndex(index);
        return entry != null && entry.getTerm() == term;
    }

    private int positionOfIndex(int index) {
        for (int i = 0; i < logEntries.size(); i++) {
            if (logEntries.get(i).getIndex() == index) {
                return i;
            }
        }
        return -1;
    }

    private void truncateFromPosition(int position) {
        for (int i = logEntries.size() - 1; i >= position; i--) {
            LogEntry removed = logEntries.remove(i);
            if (removed.getData() != null && removed.getData().getMessageId() != null) {
                messageIndexById.remove(removed.getData().getMessageId());
            }
            replicationAcksByIndex.remove(removed.getIndex());
        }
    }
}
