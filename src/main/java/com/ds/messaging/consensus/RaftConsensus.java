package com.ds.messaging.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Commit 1 scaffold for Raft consensus state and core API.
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
            // Commit 2: heartbeat broadcast transport is integrated in Commit 3.
            currentLeader = nodeId;
            lastLeaderTickMs = now;
        }
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

            for (LogEntry entry : rpc.getEntries()) {
                appendEntry(entry);
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

        // Commit 2: outbound RequestVote RPC transport is integrated later.
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
    }

    public synchronized void appendEntry(LogEntry entry) {
        if (entry == null) {
            return;
        }
        logEntries.add(entry);
    }

    public synchronized void commitEntry(LogEntry entry) {
        if (entry == null) {
            return;
        }
        entry.markCommitted();
        commitIndex.updateAndGet(existing -> Math.max(existing, entry.getIndex()));
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
}
