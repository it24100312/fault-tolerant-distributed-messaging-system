package com.ds.messaging.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal AppendEntries request model for Raft scaffolding.
 */
public class AppendEntriesRPC implements RPC {
    private final int term;
    private final String leaderId;
    private final int prevLogIndex;
    private final int prevLogTerm;
    private final List<LogEntry> entries;
    private final int leaderCommit;

    public AppendEntriesRPC(
            int term,
            String leaderId,
            int prevLogIndex,
            int prevLogTerm,
            List<LogEntry> entries,
            int leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
        this.leaderCommit = leaderCommit;
    }

    @Override
    public int getTerm() {
        return term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }
}
