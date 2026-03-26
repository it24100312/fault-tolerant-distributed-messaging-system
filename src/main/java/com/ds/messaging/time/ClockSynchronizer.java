package com.ds.messaging.time;

import com.ds.messaging.server.FailureDetector;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates cluster-level clock synchronization.
 *
 * Commit 1 scope: scaffolding + method contracts only.
 */
public class ClockSynchronizer {
    private static final Logger logger = Logger.getInstance();

    private static final long PERIODIC_SYNC_INTERVAL_SECONDS = 60L;

    private final NTPClient ntpClient;
    private final FailureDetector failureDetector;
    private final ScheduledExecutorService scheduler;

    private final AtomicLong localTimeOffsetMs = new AtomicLong(0L);
    private final AtomicBoolean periodicSyncStarted = new AtomicBoolean(false);

    public ClockSynchronizer(NTPClient ntpClient, FailureDetector failureDetector) {
        this.ntpClient = ntpClient;
        this.failureDetector = failureDetector;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Synchronize clocks using cluster-wide coordination (Berkeley algorithm).
     *
     * Commit 1 scope: contract placeholder only; full algorithm is added later.
     */
    public void synchronizeClocks(List<ServerNode> nodes) {
        List<ServerNode> safeNodes = nodes == null ? Collections.emptyList() : nodes;
        logger.info("Clock sync requested for {} nodes", safeNodes.size());

        if (failureDetector != null) {
            List<String> failedNodes = failureDetector.getFailedNodes();
            logger.debug("Failed nodes excluded from sync: {}", failedNodes);
        }

        logger.debug("Berkeley synchronization logic pending implementation");
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

    /**
     * Start periodic synchronization every 60 seconds.
     *
     * Commit 1 scope: periodic trigger placeholder without node-collection logic.
     */
    public void periodicSync() {
        if (!periodicSyncStarted.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.debug("Periodic clock sync tick");
            } catch (Exception ex) {
                logger.error("Periodic sync tick failed", ex);
            }
        }, PERIODIC_SYNC_INTERVAL_SECONDS, PERIODIC_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
