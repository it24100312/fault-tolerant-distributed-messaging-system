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

    @Override
    public void onMessageReplicated(String messageId) {
        // Commit 1: mapping replicated IDs to log commit will be implemented in Commit 3.
    }
}
