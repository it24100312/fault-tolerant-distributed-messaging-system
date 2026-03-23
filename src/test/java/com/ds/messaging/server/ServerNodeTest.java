package com.ds.messaging.server;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ServerNode health tracking
 */
public class ServerNodeTest {
    
    private ServerNode node;
    
    @Before
    public void setUp() {
        node = new ServerNode("node1", "localhost", 5000);
        node.initialize();
    }
    
    @Test
    public void testRecordHeartbeatUpdatesTimestamp() throws InterruptedException {
        // Record initial heartbeat
        node.recordHeartbeat();
        long initialTime = node.getTimeSinceLastHeartbeat();
        
        // Wait a bit
        Thread.sleep(100);
        
        // Record another heartbeat
        node.recordHeartbeat();
        long secondTime = node.getTimeSinceLastHeartbeat();
        
        // Second timestamp should be less than initial
        // (both measured recently)
        assertTrue("Second measurement should be recent", secondTime < initialTime + 100);
    }
    
    @Test
    public void testIsHealthyReturnsTrueWhenReady() {
        node.setState(NodeState.READY);
        assertTrue("Node should be healthy when READY", node.isHealthy());
    }
    
    @Test
    public void testIsHealthyReturnsFalseWhenDead() {
        node.setState(NodeState.DEAD);
        assertFalse("Node should not be healthy when DEAD", node.isHealthy());
    }
    
    @Test
    public void testIsHealthyReturnsTrueWhenSyncing() {
        node.setState(NodeState.SYNCING);
        assertTrue("Node should be healthy when SYNCING", node.isHealthy());
    }
    
    @Test
    public void testGetTimeSinceLastHeartbeatIncreases() throws InterruptedException {
        node.recordHeartbeat();
        long time1 = node.getTimeSinceLastHeartbeat();
        
        Thread.sleep(50);
        long time2 = node.getTimeSinceLastHeartbeat();
        
        assertTrue("Time since heartbeat should increase", time2 > time1);
    }
}
