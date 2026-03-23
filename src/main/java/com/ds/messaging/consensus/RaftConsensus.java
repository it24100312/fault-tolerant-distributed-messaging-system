package com.ds.messaging.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Commit 1 scaffold for Raft consensus state and core API.
 */
public class RaftConsensus {
    private final String nodeId;
    private final List<String> peerNodeIds;

    private final AtomicReference<RaftState> state = new AtomicReference<>(RaftState.FOLLOWER);
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private final AtomicInteger commitIndex = new AtomicInteger(-1);

    private final List<LogEntry> logEntries = Collections.synchronizedList(new ArrayList<>());

    private volatile String currentLeader;
    private volatile String votedFor;

    public RaftConsensus(String nodeId, List<String> peerNodeIds) {
        this.nodeId = nodeId;
        this.peerNodeIds = peerNodeIds == null ? Collections.emptyList() : new ArrayList<>(peerNodeIds);
    }

    public void onFollowerTick() {
        // Commit 1: timer and timeout behavior will be implemented in Commit 2.
    }

    public void onCandidateTick() {
        // Commit 1: election request/response flow will be implemented in Commit 2.
    }

    public void onLeaderTick() {
        // Commit 1: heartbeat and replication loop will be implemented in Commit 3.
    }

    public synchronized void handleAppendEntriesRPC(AppendEntriesRPC rpc) {
        if (rpc == null) {
            return;
        }

        onHigherTermDiscovered(rpc.getTerm());
        if (rpc.getTerm() >= currentTerm.get()) {
            state.set(RaftState.FOLLOWER);
            currentLeader = rpc.getLeaderId();

            for (LogEntry entry : rpc.getEntries()) {
                appendEntry(entry);
            }
        }
    }

    public synchronized void handleRequestVoteRPC(RequestVoteRPC rpc) {
        if (rpc == null) {
            return;
        }

        onHigherTermDiscovered(rpc.getTerm());
        if (rpc.getTerm() == currentTerm.get() && votedFor == null) {
            votedFor = rpc.getCandidateId();
        }
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
            currentTerm.set(newTerm);
            state.set(RaftState.FOLLOWER);
            votedFor = null;
        }
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
}
