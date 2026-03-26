package com.ds.messaging.time;

import com.ds.messaging.utils.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides external time synchronization contract for cluster nodes.
 *
 * Commit 1 scope: scaffolding + method contracts only.
 */
public class NTPClient {
    private static final Logger logger = Logger.getInstance();

    private final AtomicLong systemTimeOffsetMs = new AtomicLong(0L);
    private final AtomicBoolean timeSynced = new AtomicBoolean(false);
    private volatile long lastSyncEpochMs = 0L;

    /**
     * Get the current wall-clock time adjusted by latest known offset.
     */
    public long getCurrentTime() {
        return System.currentTimeMillis() + systemTimeOffsetMs.get();
    }

    /**
     * Get the tracked local-to-NTP offset in milliseconds.
     */
    public long getSystemTimeOffsetMs() {
        return systemTimeOffsetMs.get();
    }

    /**
     * Synchronize local time with an external NTP server.
     *
     * Commit 1 scope: contract placeholder only; full network sync is added later.
     */
    public void syncWithNTP(String ntpServer) {
        if (ntpServer == null || ntpServer.isBlank()) {
            logger.warn("NTP sync skipped: server is blank");
            timeSynced.set(false);
            return;
        }

        lastSyncEpochMs = System.currentTimeMillis();
        logger.info("NTP sync requested for server: {}", ntpServer);
        logger.debug("NTP sync logic pending implementation");
    }

    /**
     * Whether the client currently considers itself externally synchronized.
     */
    public boolean isTimeSynced() {
        return timeSynced.get();
    }

    /**
     * Timestamp of the last sync attempt.
     */
    public long getLastSyncEpochMs() {
        return lastSyncEpochMs;
    }
}
