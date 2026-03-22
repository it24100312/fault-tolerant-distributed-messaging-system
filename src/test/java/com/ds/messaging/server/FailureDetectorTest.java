package com.ds.messaging.server;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for FailureDetector
 */
public class FailureDetectorTest {
    
    private FailureDetector failureDetector;
    private LeaderElection leaderElection;
    private ServerNode testNode;
    
    @Before
    public void setUp() {
        leaderElection = new LeaderElection();
        failureDetector = new FailureDetector(leaderElection);
        testNode = new ServerNode("node1", "localhost", 5000);
    }
    
    @Test
    public void testHeartbeatReceivedUpdatesTime() {
        // Start monitoring node
        failureDetector.startHeartbeat(testNode);
        
        // Check node is alive initially
        assertTrue("Node should be alive after starting heartbeat", failureDetector.isNodeAlive("node1"));
        
        // Simulate heartbeat  received
        failureDetector.onHeartbeatReceived("node1");
        
        // Node should still be alive
        assertTrue("Node should be alive after heartbeat received", failureDetector.isNodeAlive("node1"));
    }
    
    @Test
    public void testHeartbeatMissedMarksAsFailedAfterThreeMisses() {
        failureDetector.startHeartbeat(testNode);
        
        // Node should be alive initially
        assertTrue("Node should be alive initially", failureDetector.isNodeAlive("node1"));
        assertTrue("Failed nodes list should be empty", failureDetector.getFailedNodes().isEmpty());
        
        // Simulate two heartbeat misses
        failureDetector.onHeartbeatMissed("node1");
        failureDetector.onHeartbeatMissed("node1");
        
        // Node should NOT be marked as failed yet
        assertFalse("Node should be in failed nodes after 2 misses", failureDetector.getFailedNodes().contains("node1"));
        
        // Simulate third heartbeat miss
        failureDetector.onHeartbeatMissed("node1");
        
        // NOW node should be marked as failed
        assertTrue("Node should be in failed nodes after 3 misses", failureDetector.getFailedNodes().contains("node1"));
        assertFalse("Node should not be alive when in failed list", failureDetector.isNodeAlive("node1"));
    }
    
    @Test
    public void testNodeRecoveryRemovesFromFailedList() {
        failureDetector.startHeartbeat(testNode);
        
        // Mark node as failed (3 misses)
        failureDetector.onHeartbeatMissed("node1");
        failureDetector.onHeartbeatMissed("node1");
        failureDetector.onHeartbeatMissed("node1");
        
        assertTrue("Node should be in failed list", failureDetector.getFailedNodes().contains("node1"));
        
        // Simulate heartbeat reception (node recovered)
        failureDetector.onHeartbeatReceived("node1");
        
        // Node should be removed from failed list
        assertFalse("Node should be removed from failed list after heartbeat", failureDetector.getFailedNodes().contains("node1"));
        assertTrue("Node should be alive after recovery", failureDetector.isNodeAlive("node1"));
    }
    
    @Test
    public void testGetFailedNodesReturnsSnapshotList() {
        failureDetector.startHeartbeat(testNode);
        
        // Mark node as failed
        failureDetector.onHeartbeatMissed("node1");
        failureDetector.onHeartbeatMissed("node1");
        failureDetector.onHeartbeatMissed("node1");
        
        // Get failed nodes list
        java.util.List<String> failedNodes = failureDetector.getFailedNodes();
        
        // Verify it contains node1
        assertTrue("Failed nodes should contain node1", failedNodes.contains("node1"));
        
        // Get another snapshot - should have same content
        java.util.List<String> failedNodes2 = failureDetector.getFailedNodes();
        assertEquals("Failed nodes list should be consistent", failedNodes, failedNodes2);
    }
}
