package com.ds.messaging.server;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for LeaderElection
 */
public class LeaderElectionTest {
    
    private LeaderElection leaderElection;
    private ServerNode node1, node2, node3;
    
    @Before
    public void setUp() {
        leaderElection = new LeaderElection();
        node1 = new ServerNode("node1", "localhost", 5001);
        node2 = new ServerNode("node2", "localhost", 5002);
        node3 = new ServerNode("node3", "localhost", 5003);
        
        node1.initialize();
        node2.initialize();
        node3.initialize();
    }
    
    @Test
    public void testStartElectionWithHealthyCandidates() {
        List<ServerNode> candidates = Arrays.asList(node1, node2, node3);
        
        ServerNode leader = leaderElection.startElection(candidates);
        
        assertNotNull("Leader should be elected", leader);
        assertEquals("Elected leader should be node3 (highest ID)", "node3", leader.getNodeId());
        assertEquals("Leader should be node3", node3, leaderElection.getCurrentLeader());
    }
    
    @Test
    public void testTriggerReelectionClearsCurrentLeader() {
        leaderElection.setLeader(node1);
        assertEquals("Leader should be node1", node1, leaderElection.getCurrentLeader());
        
        leaderElection.triggerReelection();
        
        assertNull("Leader should be cleared after re-election trigger", leaderElection.getCurrentLeader());
    }
    
    @Test
    public void testTriggerReelectionIncrementsTermNumber() {
        long term1 = leaderElection.getCurrentTerm();
        
        leaderElection.triggerReelection();
        
        long term2 = leaderElection.getCurrentTerm();
        assertEquals("Term should increment on re-election", term1 + 1, term2);
    }
    
    @Test
    public void testIsLeaderAliveReturnsTrueWhenLeaderHealthy() {
        leaderElection.setLeader(node1);
        node1.setState(NodeState.READY);
        
        assertTrue("Leader should be alive when healthy", leaderElection.isLeaderAlive());
    }
    
    @Test
    public void testIsLeaderAliveReturnsFalseWhenNoLeader() {
        assertFalse("No leader should not be alive", leaderElection.isLeaderAlive());
    }
    
    @Test
    public void testSetLeaderUpdatesCurrentLeader() {
        assertNull("Initially no leader", leaderElection.getCurrentLeader());
        
        leaderElection.setLeader(node2);
        
        assertEquals("Leader should be node2", node2, leaderElection.getCurrentLeader());
    }
}
