package com.ds.messaging.consensus;

import static org.junit.Assert.*;

import java.util.Collections;

import org.junit.Test;

import com.ds.messaging.client.Message;

public class ConsensusManagerTest {

    @Test
    public void testApplyEntriesAppliesCommittedEntriesInOrder() {
        RaftConsensus raft = new RaftConsensus("n1", Collections.emptyList());
        raft.becomeLeader();

        LogEntry e0 = new LogEntry(1, 0, new Message("n1", "a"));
        LogEntry e1 = new LogEntry(1, 1, new Message("n1", "b"));
        raft.appendEntry(e0);
        raft.appendEntry(e1);
        raft.commitEntry(e0);
        raft.commitEntry(e1);

        ConsensusManager manager = new ConsensusManager(raft);
        manager.applyEntries();

        assertEquals(1, manager.getLastAppliedIndex());
        assertEquals(2, manager.getAppliedEntriesSnapshot().size());
        assertEquals(0, manager.getAppliedEntriesSnapshot().get(0).getIndex());
        assertEquals(1, manager.getAppliedEntriesSnapshot().get(1).getIndex());
    }

    @Test
    public void testOnMessageReplicatedCommitsSingleNodeLeaderEntry() {
        RaftConsensus raft = new RaftConsensus("n1", Collections.emptyList());
        raft.becomeLeader();

        Message message = new Message("n1", "payload");
        LogEntry entry = new LogEntry(1, 0, message);
        raft.appendEntry(entry);

        ConsensusManager manager = new ConsensusManager(raft);
        manager.onMessageReplicated(message.getMessageId());

        assertEquals(0, manager.getCommitIndex());
        assertEquals(0, manager.getLastAppliedIndex());
        assertEquals(1, manager.getAppliedEntriesSnapshot().size());
    }
}
