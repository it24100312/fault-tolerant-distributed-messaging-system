package com.ds.messaging.consensus;

import com.ds.messaging.replication.ConsensusPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates consensus interactions and offers commit-index access.
 */
public class ConsensusManager implements ConsensusPort {
    private final RaftConsensus raftConsensus;
    private int lastAppliedIndex = -1;
    private final List<LogEntry> appliedEntries = new ArrayList<>();

    public ConsensusManager(RaftConsensus raftConsensus) {
        this.raftConsensus = raftConsensus;
    }

    public void handleRPC(RPC rpc) {
        if (rpc == null || raftConsensus == null) {
            return;
        }

        if (rpc instanceof AppendEntriesRPC) {
            raftConsensus.handleAppendEntriesRPC((AppendEntriesRPC) rpc);
        } else if (rpc instanceof RequestVoteRPC) {
            raftConsensus.handleRequestVoteRPC((RequestVoteRPC) rpc);
        }
    }

    public void applyEntries() {
        if (raftConsensus == null) {
            return;
        }

        int latestCommitted = raftConsensus.getCommitIndex();
        for (int idx = lastAppliedIndex + 1; idx <= latestCommitted; idx++) {
            LogEntry entry = raftConsensus.getEntryByIndex(idx);
            if (entry == null || !entry.isCommitted()) {
                break;
            }
            appliedEntries.add(entry);
            lastAppliedIndex = idx;
        }
    }

    public int getCommitIndex() {
        if (raftConsensus == null) {
            return -1;
        }
        return raftConsensus.getCommitIndex();
    }

    public void onTermChange(int newTerm) {
        if (raftConsensus == null) {
            return;
        }
        raftConsensus.onHigherTermDiscovered(newTerm);
    }

    public void tick() {
        if (raftConsensus == null) {
            return;
        }

        RaftState state = raftConsensus.getState();
        switch (state) {
            case FOLLOWER:
                raftConsensus.onFollowerTick();
                break;
            case CANDIDATE:
                raftConsensus.onCandidateTick();
                break;
            case LEADER:
                raftConsensus.onLeaderTick();
                break;
            default:
                break;
        }
    }

    public void onLeaderFailure(String failedNodeId) {
        if (raftConsensus == null) {
            return;
        }
        raftConsensus.triggerElectionIfLeaderFailed(failedNodeId);
    }

    @Override
    public void onMessageReplicated(String messageId) {
        if (raftConsensus == null) {
            return;
        }

        boolean committed = raftConsensus.commitReplicatedMessage(messageId);
        if (committed) {
            applyEntries();
        }
    }

    public int getLastAppliedIndex() {
        return lastAppliedIndex;
    }

    public List<LogEntry> getAppliedEntriesSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(appliedEntries));
    }
}
