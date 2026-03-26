package com.ds.messaging.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ds.messaging.utils.Config;
import com.ds.messaging.utils.Logger;

/**
 * Main messaging server that manages a cluster of nodes.
 */
public class MessagingServer {
    private static final Logger logger = Logger.getInstance();

    private final Config config;
    private final Map<String, ServerNode> nodes;

    private ScheduledExecutorService executor;
    private boolean isRunning;
    private LeaderElection leaderElection;
    private FailureDetector failureDetector;

    public MessagingServer(Config config) {
        this.config = config;
        this.nodes = new ConcurrentHashMap<>();
        this.isRunning = false;
        logger.info("MessagingServer created with config");
    }

    public synchronized void start() {
        if (isRunning) {
            logger.warn("Server already running");
            return;
        }

        executor = Executors.newScheduledThreadPool(5);
        leaderElection = new LeaderElection();
        failureDetector = new FailureDetector(leaderElection);
        isRunning = true;

        for (ServerNode node : nodes.values()) {
            node.initialize();
            node.connect();
            failureDetector.startHeartbeat(node);
        }

        logger.info("MessagingServer started with {} nodes", nodes.size());
    }

    public synchronized void stop() {
        if (!isRunning) {
            logger.warn("Server not running");
            return;
        }

        isRunning = false;

        for (ServerNode node : nodes.values()) {
            node.shutdown();
        }

        if (failureDetector != null) {
            failureDetector.shutdown();
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        logger.info("MessagingServer stopped");
    }

    public void addNode(String nodeId, String host, int port) {
        ServerNode node = new ServerNode(nodeId, host, port);
        nodes.put(nodeId, node);

        if (isRunning && failureDetector != null) {
            node.initialize();
            node.connect();
            failureDetector.startHeartbeat(node);
        }
    }

    public ServerNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<ServerNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public ServerNode getLeader() {
        if (leaderElection != null) {
            return leaderElection.getCurrentLeader();
        }
        return null;
    }

    public void onNodeFailure(String nodeId) {
        logger.error("Node failed: {}", nodeId);
        removeFailedNode(nodeId);
        notifyReplication(nodeId);
        notifyConsensus(nodeId);
    }

    public void removeFailedNode(String nodeId) {
        ServerNode node = nodes.remove(nodeId);
        if (node != null && failureDetector != null) {
            failureDetector.stopHeartbeat(nodeId);
            logger.info("Removed failed node: {}", nodeId);
        }
    }

    public void notifyReplication(String failedNodeId) {
        logger.debug("Replication module notified of node failure: {}", failedNodeId);
    }

    public void notifyConsensus(String failedNodeId) {
        logger.debug("Consensus module notified of node failure: {}", failedNodeId);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public Config getConfig() {
        return config;
    }
}
