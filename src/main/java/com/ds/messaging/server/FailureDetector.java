package com.ds.messaging.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.ds.messaging.utils.Logger;

/**
 * Failure detection based on heartbeats.
 */
public class FailureDetector {
    private static final Logger logger = Logger.getInstance();

    private static final long HEARTBEAT_INTERVAL_MS = 2000L;
    private static final long HEARTBEAT_TIMEOUT_MS = 5000L;
    private static final int MAX_FAILURES_BEFORE_DEAD = 3;

    private final Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private final Set<String> failedNodes = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor;
    private final LeaderElection leaderElection;

    public FailureDetector(LeaderElection leaderElection) {
        this.leaderElection = leaderElection;
        this.executor = Executors.newScheduledThreadPool(1);
        logger.info("FailureDetector initialized");
    }

    public void startHeartbeat(ServerNode node) {
        if (node == null) {
            return;
        }

        String nodeId = node.getNodeId();
        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        failureCount.put(nodeId, 0);

        ScheduledFuture<?> existingTask = heartbeatTasks.remove(nodeId);
        if (existingTask != null) {
            existingTask.cancel(false);
        }

        ScheduledFuture<?> task = executor.scheduleAtFixedRate(() -> {
            if (node.getState() != NodeState.DEAD && node.isListenerRunning()) {
                // In local simulation mode, a running listener means this node is still alive.
                node.recordHeartbeat();
                onHeartbeatReceived(nodeId);
                return;
            }

            long now = System.currentTimeMillis();
            long lastSeen = lastHeartbeatTime.getOrDefault(nodeId, now);
            if (now - lastSeen > HEARTBEAT_TIMEOUT_MS) {
                onHeartbeatMissed(nodeId);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        heartbeatTasks.put(nodeId, task);
        logger.info("Started heartbeat monitoring for node: {}", nodeId);
    }

    public void stopHeartbeat(String nodeId) {
        if (nodeId == null) {
            return;
        }

        ScheduledFuture<?> task = heartbeatTasks.remove(nodeId);
        if (task != null) {
            task.cancel(false);
        }

        lastHeartbeatTime.remove(nodeId);
        failureCount.remove(nodeId);
        failedNodes.remove(nodeId);

        logger.info("Stopped heartbeat monitoring for node: {}", nodeId);
    }

    public void onHeartbeatReceived(String nodeId) {
        if (nodeId == null) {
            return;
        }

        lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
        failureCount.put(nodeId, 0);

        if (failedNodes.remove(nodeId)) {
            logger.info("Node recovered: {}", nodeId);
        }
    }

    public boolean isNodeAlive(String nodeId) {
        if (nodeId == null || !lastHeartbeatTime.containsKey(nodeId)) {
            return false;
        }

        // In this local simulation, we treat a monitored node as alive until it is
        // explicitly marked failed by the miss-threshold logic.
        return !failedNodes.contains(nodeId);
    }

    public List<String> getFailedNodes() {
        return new ArrayList<>(failedNodes);
    }

    public void onHeartbeatMissed(String nodeId) {
        if (nodeId == null) {
            return;
        }

        int failures = failureCount.getOrDefault(nodeId, 0) + 1;
        failureCount.put(nodeId, failures);

        if (failures >= MAX_FAILURES_BEFORE_DEAD && failedNodes.add(nodeId)) {
            logger.warn("Node marked as failed: {} ({} failed heartbeats)", nodeId, failures);
            if (leaderElection != null) {
                leaderElection.onLeaderFailure(nodeId);
            }
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> task : heartbeatTasks.values()) {
            task.cancel(false);
        }
        heartbeatTasks.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        logger.info("FailureDetector shutdown");
    }
}
