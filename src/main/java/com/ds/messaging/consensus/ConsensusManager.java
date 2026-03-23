package com.ds.messaging.consensus;

import com.ds.messaging.replication.ConsensusPort;

/**
 * Coordinates consensus interactions and offers commit-index access.
 */
public class ConsensusManager implements ConsensusPort {
    private final RaftConsensus raftConsensus;

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
        // Commit 1: state-machine application loop will be implemented in Commit 3.
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
        // Commit 1: mapping replicated IDs to log commit will be implemented in Commit 3.
    }
}
