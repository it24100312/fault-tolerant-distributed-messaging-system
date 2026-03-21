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
    private Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
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
        String nodeId = node.getNodeId();
        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        failureCount.put(nodeId, 0);
        
        // Schedule periodic heartbeat check every HEARTBEAT_INTERVAL_MS
        ScheduledFuture<?> task = executor.scheduleAtFixedRate(() -> {
            long timeSinceLastHB = System.currentTimeMillis() - lastHeartbeatTime.getOrDefault(nodeId, System.currentTimeMillis());
            
            // If no heartbeat received in HEARTBEAT_TIMEOUT_MS, increment failure counter
            if (timeSinceLastHB > HEARTBEAT_TIMEOUT_MS) {
                onHeartbeatMissed(nodeId);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        heartbeatTasks.put(nodeId, task);
        logger.info("Started heartbeat monitoring for node: {}", nodeId);
    }Cancel the scheduled heartbeat task
        ScheduledFuture<?> task = heartbeatTasks.remove(nodeId);
        if (task != null) {
            task.cancel(false);
        }
        
        //
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
    
    /**Update lastHeartbeatTime for this node
        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        // Reset failure count to 0
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
        if Never heard from this node
        if (!lastHeartbeatTime.containsKey(nodeId)) {
            return false;
        }
        
        // Check if recent heartbeat AND not in failed listlong timeSinceLastHB = System.currentTimeMillis() - lastHeartbeatTime.get(nodeId);
        return timeSinceLastHB < HEARTBEAT_TIMEOUT_MS && !failedNodes.contains(nodeId);
    }
    
    /**
     * Get list of currently failed nodes
     */
    public List<String> getFailedNodes() {
        // Return snapshot of failed nodes (thread-safe)
        return new ArrayList<>(failedNodes);
    }
    
    /**
     * Called when heartbeat is missed
     */
    public Increment failure count
        int failures = failureCount.getOrDefault(nodeId, 0) + 1;
        failureCount.put(nodeId, failures);
        
        // If exceeds MAX_FAILURES_BEFORE_DEAD, mark as failed
        if (failures >= MAX_FAILURES_BEFORE_DEAD) {
            failedNodes.add(nodeId);
            logger.warn("Node marked as failed: {} ({} failed heartbeats)", nodeId, failures);
            
            // Trigger re-election if failed node is
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
           Cancel all heartbeat tasks
        for (ScheduledFuture<?> task : heartbeatTasks.values()) {
            task.cancel(false);
        }
        heartbeatTasks.clear();
        
        // Shutdown executorination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        logger.info("FailureDetector shutdown");
    }
}
