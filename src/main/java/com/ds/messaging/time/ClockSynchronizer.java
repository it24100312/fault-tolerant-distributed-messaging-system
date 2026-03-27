package com.ds.messaging.time;

import com.ds.messaging.server.FailureDetector;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates cluster-level clock synchronization.
 */
public class ClockSynchronizer {
    private static final Logger logger = Logger.getInstance();

    private static final long PERIODIC_SYNC_INTERVAL_SECONDS = 60L;

    private final NTPClient ntpClient;
    private final FailureDetector failureDetector;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<List<ServerNode>> lastKnownNodes =
            new AtomicReference<>(Collections.emptyList());

    private final AtomicLong localTimeOffsetMs = new AtomicLong(0L);
    private final AtomicBoolean periodicSyncStarted = new AtomicBoolean(false);
    private final AtomicLong lastSyncEpochMs = new AtomicLong(0L);
    private final Map<String, Long> nodeCorrectionsMs = new ConcurrentHashMap<>();

    public ClockSynchronizer(NTPClient ntpClient, FailureDetector failureDetector) {
        this.ntpClient = ntpClient;
        this.failureDetector = failureDetector;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Synchronize clocks using cluster-wide coordination (Berkeley algorithm).
     */
    public void synchronizeClocks(List<ServerNode> nodes) {
        List<ServerNode> safeNodes = nodes == null
                ? Collections.emptyList()
                : new ArrayList<>(nodes);
        lastKnownNodes.set(safeNodes);

        logger.info("Clock sync requested for {} nodes", safeNodes.size());

        Set<String> failedNodeIds = new HashSet<>();
        if (failureDetector != null) {
            failedNodeIds.addAll(failureDetector.getFailedNodes());
            logger.debug("Failed nodes excluded from sync: {}", failedNodeIds);
        }

        synchronized (this) {
            long localBaseTime = resolveLocalBaseTime();
            long totalTime = localBaseTime;
            int participantCount = 1;

            for (ServerNode node : safeNodes) {
                if (!isEligibleForSync(node, failedNodeIds)) {
                    continue;
                }

                long nodeTime = resolveNodeTime(node);
                totalTime += nodeTime;
                participantCount++;
            }

            long averageTime = totalTime / participantCount;

            long localCorrection = averageTime - localBaseTime;
            localTimeOffsetMs.set(localCorrection);

            nodeCorrectionsMs.clear();
            for (ServerNode node : safeNodes) {
                if (!isEligibleForSync(node, failedNodeIds)) {
                    continue;
                }

                long nodeTime = resolveNodeTime(node);
                nodeCorrectionsMs.put(node.getNodeId(), averageTime - nodeTime);
            }

            long now = System.currentTimeMillis();
            lastSyncEpochMs.set(now);
            logger.info("Clock sync complete: participants={}, localCorrection={}ms",
                    participantCount, localCorrection);
        }
    }

    /**
     * Get synchronized cluster time using local adjustment.
     */
    public long getClusterTime() {
        long baseTime = ntpClient == null ? System.currentTimeMillis() : ntpClient.getCurrentTime();
        return baseTime + localTimeOffsetMs.get();
    }

    /**
     * Get current local adjustment offset in milliseconds.
     */
    public long getLocalTimeOffset() {
        return localTimeOffsetMs.get();
    }

    public long getNodeCorrectionMs(String nodeId) {
        return nodeCorrectionsMs.getOrDefault(nodeId, 0L);
    }

    public long getLastSyncEpochMs() {
        return lastSyncEpochMs.get();
    }

    public boolean isPeriodicSyncRunning() {
        return periodicSyncStarted.get();
    }

    /**
     * Start periodic synchronization every 60 seconds.
     */
    public void periodicSync() {
        if (!periodicSyncStarted.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                synchronizeClocks(lastKnownNodes.get());
            } catch (Exception ex) {
                logger.error("Periodic sync tick failed", ex);
            }
        }, PERIODIC_SYNC_INTERVAL_SECONDS, PERIODIC_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        periodicSyncStarted.set(false);
        scheduler.shutdownNow();
    }

    protected long resolveLocalBaseTime() {
        return ntpClient == null ? System.currentTimeMillis() : ntpClient.getCurrentTime();
    }

    protected long resolveNodeTime(ServerNode node) {
        return System.currentTimeMillis();
    }

    private boolean isEligibleForSync(ServerNode node, Set<String> failedNodeIds) {
        return node != null
                && node.getNodeId() != null
                && node.isHealthy()
                && !failedNodeIds.contains(node.getNodeId());
    }
}
