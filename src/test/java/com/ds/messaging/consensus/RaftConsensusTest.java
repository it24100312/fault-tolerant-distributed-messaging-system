package com.ds.messaging.consensus;

import com.ds.messaging.client.Message;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaftConsensusTest {

    @Test
    public void testFollowerTimeoutTransitionsToCandidate() throws InterruptedException {
        RaftConsensus raft = new RaftConsensus("n1", Arrays.asList("n2", "n3"));

        Thread.sleep(350);
        raft.onFollowerTick();

        assertEquals(RaftState.CANDIDATE, raft.getState());
    }

    @Test
    public void testCandidateBecomesLeaderAfterMajorityVotes() {
        RaftConsensus raft = new RaftConsensus("n1", Arrays.asList("n2", "n3"));

        raft.startElection();
        int term = raft.getCurrentTerm();
        raft.registerVote("n2", term, true);

        assertEquals(RaftState.LEADER, raft.getState());
        assertEquals("n1", raft.getCurrentLeader());
    }

    @Test
    public void testAppendEntriesCommitsUpToLeaderCommit() {
        RaftConsensus follower = new RaftConsensus("n2", Arrays.asList("n1", "n3"));

        LogEntry entry = new LogEntry(1, 0, new Message("n1", "m1"));
        AppendEntriesRPC rpc = new AppendEntriesRPC(1, "n1", -1, -1, Collections.singletonList(entry), 0);

        follower.handleAppendEntriesRPC(rpc);

        assertEquals(0, follower.getCommitIndex());
        assertTrue(follower.getEntryByIndex(0).isCommitted());
    }

    @Test
    public void testLeaderCommitsWhenQuorumAckReceived() {
        RaftConsensus leader = new RaftConsensus("n1", Arrays.asList("n2", "n3"));
        leader.becomeLeader();

        LogEntry entry = new LogEntry(1, 0, new Message("n1", "payload"));
        leader.appendEntry(entry);

        leader.registerAppendAck("n2", leader.getCurrentTerm(), 0, true);

        assertEquals(0, leader.getCommitIndex());
        assertTrue(leader.getEntryByIndex(0).isCommitted());
    }

    @Test
    public void testLogConvergesOnConflictingEntry() {
        RaftConsensus follower = new RaftConsensus("n2", List.of("n1", "n3"));

        follower.appendEntry(new LogEntry(1, 0, new Message("nX", "old")));

        AppendEntriesRPC rpc = new AppendEntriesRPC(
                2,
                "n1",
                -1,
                -1,
                Collections.singletonList(new LogEntry(2, 0, new Message("n1", "new"))),
                -1);
        follower.handleAppendEntriesRPC(rpc);

        assertEquals(2, follower.getEntryByIndex(0).getTerm());
        assertEquals("new", follower.getEntryByIndex(0).getData().getContent());
    }
}
