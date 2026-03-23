package com.ds.messaging.consensus;

import java.util.Objects;

import com.ds.messaging.client.Message;

/**
 * Single entry in the Raft replicated log.
 */
public class LogEntry {
    private final int term;
    private final int index;
    private final Message data;
    private volatile boolean committed;

    public LogEntry(int term, int index, Message data) {
        this.term = term;
        this.index = index;
        this.data = data;
        this.committed = false;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public Message getData() {
        return data;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void markCommitted() {
        this.committed = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LogEntry)) {
            return false;
        }
        LogEntry other = (LogEntry) obj;
        return term == other.term
                && index == other.index
                && committed == other.committed
                && Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, index, data, committed);
    }
}
