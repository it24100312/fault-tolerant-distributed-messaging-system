package com.ds.messaging.server;

import com.ds.messaging.utils.Logger;
import java.util.*;
import java.util.concurrent.*;

/**
 * Failure detection using heartbeat mechanism.
 * 
 * Responsibilities:
 * - Send periodic heartbeats to all nodes
 * - Detect failed nodes when heartbeats stop
 * - Report failures to leader election
 * - Re-try failed nodes periodically
 * 
 * TODO: Implement heartbeat-based failure detection
 */
public class FailureDetector {
    private static final Logger logger = Logger.getInstance();
    
    // Heartbeat and timeout configuration
    private static final long HEARTBEAT_INTERVAL_MS = 2000;  // Send every 2 seconds
    private static final long HEARTBEAT_TIMEOUT_MS = 5000;   // Consider dead after 5 seconds
    private static final int MAX_FAILURES_BEFORE_DEAD = 3;
    
    private Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private Set<String> failedNodes = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService executor;
    private LeaderElection leaderElection;
    
    /**
     * Initialize failure detector
     */
    public FailureDetector(LeaderElection leaderElection) {
        // TODO: Implement initialization
        this.leaderElection = leaderElection;
        this.executor = Executors.newScheduledThreadPool(1);
        logger.info("FailureDetector initialized");
    }
    
    /**
     * Start heartbeat monitoring for a node
     */
    public void startHeartbeat(ServerNode node) {
        // TODO: Implement heartbeat start
        // 1. Record initial heartbeat time
        // 2. Start periodic heartbeat task (every HEARTBEAT_INTERVAL_MS)
        // 3. Track the task for later cancellation
        
        String nodeId = node.getNodeId();
        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        failureCount.put(nodeId, 0);
        
        logger.info("Started heartbeat monitoring for node: {}", nodeId);
    }
    
    /**
     * Stop heartbeat monitoring for a node
     */
    public void stopHeartbeat(String nodeId) {
        // TODO: Implement heartbeat stop
        // 1. Cancel the scheduled heartbeat task
        // 2. Clean up internal state
        lastHeartbeatTime.remove(nodeId);
        failureCount.remove(nodeId);
        failedNodes.remove(nodeId);
        
        logger.info("Stopped heartbeat monitoring for node: {}", nodeId);
    }
    
    /**
     * Record successful heartbeat from node
     */
    public void onHeartbeatReceived(String nodeId) {
        // TODO: Implement heartbeat receive
        // 1. Update lastHeartbeatTime for this node
        // 2. Reset failure count to 0
        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        failureCount.put(nodeId, 0);
        
        // If node was previously failed, mark as recovered
        if (failedNodes.contains(nodeId)) {
            failedNodes.remove(nodeId);
            logger.info("Node recovered: {}", nodeId);
        }
    }
    
    /**
     * Check if a specific node is alive
     */
    public boolean isNodeAlive(String nodeId) {
        // TODO: Implement alive check
        if (!lastHeartbeatTime.containsKey(nodeId)) {
            return false;  // Never heard from this node
        }
        
        long timeSinceLastHB = System.currentTimeMillis() - lastHeartbeatTime.get(nodeId);
        return timeSinceLastHB < HEARTBEAT_TIMEOUT_MS && !failedNodes.contains(nodeId);
    }
    
    /**
     * Get list of currently failed nodes
     */
    public List<String> getFailedNodes() {
        // TODO: Implement failed nodes list
        return new ArrayList<>(failedNodes);
    }
    
    /**
     * Called when heartbeat is missed
     */
    public void onHeartbeatMissed(String nodeId) {
        // TODO: Implement missed heartbeat handling
        // 1. Increment failure count
        // 2. If exceeds MAX_FAILURES_BEFORE_DEAD, mark as failed
        // 3. Trigger re-election if failed node is leader
        
        int failures = failureCount.getOrDefault(nodeId, 0) + 1;
        failureCount.put(nodeId, failures);
        
        if (failures >= MAX_FAILURES_BEFORE_DEAD) {
            failedNodes.add(nodeId);
            logger.warn("Node marked as failed: {} ({}  failed heartbeats)", nodeId, failures);
            
            // Check if failed node was the leader
            ServerNode leader = leaderElection.getCurrentLeader();
            if (leader != null && leader.getNodeId().equals(nodeId)) {
                logger.warn("Leader failed! Triggering re-election");
                leaderElection.triggerReelection();
            }
        }
    }
    
    /**
     * Shutdown failure detector
     */
    public void shutdown() {
        // TODO: Implement shutdown
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        logger.info("FailureDetector shutdown");
    }
}
