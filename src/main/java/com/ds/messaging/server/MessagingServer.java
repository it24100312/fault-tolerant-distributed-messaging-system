package com.ds.messaging.server;

import com.ds.messaging.utils.Config;
import com.ds.messaging.utils.Logger;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main messaging server that manages a cluster of nodes.
 * 
 * Responsibilities:
 * - Initialize and manage server clusters
 * - Accept client connections
 * - Coordinate between nodes
 * - Monitor node health
 * 
 * TODO: Implement all methods
 */
public class MessagingServer {
    private static final Logger logger = Logger.getInstance();
    
    private Config config;
    private Map<String, ServerNode> nodes;
    private ScheduledExecutorService executor;
    private boolean isRunning;
    private LeaderElection leaderElection;
    private FailureDetector failureDetector;
    
    /**
     * Initialize messaging server with given configuration
     */
    public MessagingServer(Config config) {
        this.config = config;
        this.nodes = new ConcurrentHashMap<>();
        this.isRunning = false;
        logger.info("MessagingServer created with config");
    }
    
    /**
     * Start the messaging server
     */
    public void start() {
        if (isRunning) {
            logger.warn("Server already running");
            return;
        }
        
        // Initialize executor service
        executor = Executors.newScheduledThreadPool(5);
        
        // Initialize leader election
        leaderElection = new LeaderElection();
        
        // Initialize failure detector
        failureDetector = new FailureDetector(leaderElection);
        if (!isRunning) {
            logger.warn("Server not running");
            return;
        }
        
        isRunning = false;
        
        // Shutdown all nodes
        for (ServerNode node : nodes.values()) {
            node.shutdown();
        }
        
        // Stop failure detector
        if (failureDetector != null) {
            failureDetector.shutdown();
        }
        
        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
        
        ServerNode node = new ServerNode(nodeId, host, port);
        nodes.put(nodeId, node);
        
        // If server is running, start heartbeat immediately
        if (isRunning && failureDetector != null) {
            node.initialize();
            node.connect();
            failureDetector.startHeartbeat(node);
        }
        
        logger.info("MessagingServer started with {} nodes", nodes.size());
    }
    
    /**
     * Stop the messaging server cleanly
     */
    public void stop() {
        // TODO: Implement shutdown
        // 1. Set isRunning = false
        // 2. Shutdown all nodes
        // 3. Stop failure detector
        // 4. Shutdown executor
        // 5. Clean up resources
        logger.info("MessagingServer stopping...");
    }
    
    /**
     * Add a new node to the cluster
     */
    public void addNode(String nodeId, String host, int port) {
        // TODO: Implement node addition
        ServerNode node = new ServerNode(nodeId, host, port);
        nodes.put(nodeId, node);
        
    
    /**
     * Get a specific node by ID
     */
    public ServerNode getNode(String nodeId) {
        // TODO: Implement node retrieval
        return nodes.get(nodeId);
    }
    /**
     * Get all nodes in the cluster
     */
    public List<ServerNode> getAllNodes() {
        // TODO: Implement retrieval of all nodes
        return new ArrayList<>(nodes.values());
    }
    
    /**
     * Get current cluster leader
     */
    public ServerNode getLeader() {
        // TODO: Implement leader retrieval
        // Use leaderElection.getCurrentLeader()
        if (leaderElection != null) {
            return leaderElection.getCurrentLeader();
        }
        return null;
    }
    
    /**
     * Handle node failure event (called by FailureDetector)
     */
    public void onNodeFailure(String nodeId) {
        logger.error("Node failed: {}", nodeId);
        removeFailedNode(nodeId);
        notifyReplication(nodeId);
        notifyConsensus(nodeId);
    }
    
    /**
     * Remove failed node from cluster
     */
    public void removeFailedNode(String nodeId) {
        ServerNode node = nodes.remove(nodeId);
        if (node != null) {
            failureDetector.stopHeartbeat(nodeId);
            logger.info("Removed failed node: {}", nodeId);
        }
    }
    
    /**
     * Notify replication module about node failure
     */
    public void notifyReplication(String failedNodeId) {
        // Member 2 will check failureDetector.getFailedNodes()
        // No explicit notification needed - they watch the failed nodes list
        logger.debug("Replication module notified of node failure: {}", failedNodeId);
    }
    
    /**
     * Notify consensus module about node failure
     */
    public void notifyConsensus(String failedNodeId) {
        // Member 4 will check failureDetector.getFailedNodes()
        // for quorum calculations
        logger.debug("Consensus module notified of node failure: {}", failedNodeId);
    }
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Get number of nodes in cluster
     */
    public int getNodeCount() {
        return nodes.size();
    }
}
