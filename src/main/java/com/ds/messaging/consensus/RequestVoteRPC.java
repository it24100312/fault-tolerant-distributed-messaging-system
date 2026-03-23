package com.ds.messaging.consensus;

/**
 * Minimal RequestVote request model for Raft scaffolding.
 */
public class RequestVoteRPC implements RPC {
    private final int term;
    private final String candidateId;
    private final int lastLogIndex;
    private final int lastLogTerm;

    public RequestVoteRPC(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    @Override
    public int getTerm() {
        return term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }
}
