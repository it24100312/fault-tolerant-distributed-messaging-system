package com.ds.messaging.time;

import com.ds.messaging.utils.Logger;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides external time synchronization contract for cluster nodes.
 *
 * Commit 1 scope: scaffolding + method contracts only.
 */
public class NTPClient {
    private static final Logger logger = Logger.getInstance();

    private static final int NTP_PORT = 123;
    private static final int NTP_PACKET_SIZE = 48;
    private static final int NTP_TIMEOUT_MS = 1000;
    private static final long NTP_TO_UNIX_EPOCH_MS = 2_208_988_800_000L;
    private static final long SYNC_STALE_AFTER_MS = TimeUnit.HOURS.toMillis(2);
    private static final long HOURLY_SYNC_PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    private final AtomicLong systemTimeOffsetMs = new AtomicLong(0L);
    private final AtomicBoolean timeSynced = new AtomicBoolean(false);
    private final AtomicBoolean periodicSyncStarted = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
     */
    public void syncWithNTP(String ntpServer) {
        if (ntpServer == null || ntpServer.isBlank()) {
            logger.warn("NTP sync skipped: server is blank");
            timeSynced.set(false);
            return;
        }

        long localBefore = System.currentTimeMillis();
        try {
            long ntpTime = queryNtpTimeMillis(ntpServer, NTP_TIMEOUT_MS);
            long localAfter = System.currentTimeMillis();

            long estimatedLocalNow = (localBefore + localAfter) / 2L;
            long offset = ntpTime - estimatedLocalNow;

            systemTimeOffsetMs.set(offset);
            lastSyncEpochMs = localAfter;
            timeSynced.set(true);

            logger.info("NTP sync successful with {} (offset={}ms)", ntpServer, offset);
        } catch (SocketTimeoutException timeoutEx) {
            timeSynced.set(false);
            logger.warn("NTP sync timeout for {}", ntpServer);
        } catch (Exception ex) {
            timeSynced.set(false);
            logger.warn("NTP sync failed for {}: {}", ntpServer, ex.getMessage());
        }
    }

    /**
     * Whether the client currently considers itself externally synchronized.
     */
    public boolean isTimeSynced() {
        if (!timeSynced.get()) {
            return false;
        }
        return (System.currentTimeMillis() - lastSyncEpochMs) <= SYNC_STALE_AFTER_MS;
    }

    /**
     * Timestamp of the last sync attempt.
     */
    public long getLastSyncEpochMs() {
        return lastSyncEpochMs;
    }

    /**
     * Start periodic NTP synchronization every hour.
     */
    public void startHourlySync(String ntpServer) {
        if (!periodicSyncStarted.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(() -> syncWithNTP(ntpServer),
                0L, HOURLY_SYNC_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop periodic synchronization tasks.
     */
    public void stopHourlySync() {
        periodicSyncStarted.set(false);
        scheduler.shutdownNow();
    }

    protected long queryNtpTimeMillis(String ntpServer, int timeoutMs) throws IOException {
        byte[] request = new byte[NTP_PACKET_SIZE];
        request[0] = 0x1B;

        InetAddress serverAddress = InetAddress.getByName(ntpServer);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            DatagramPacket requestPacket = new DatagramPacket(request, request.length, serverAddress, NTP_PORT);
            socket.send(requestPacket);

            byte[] response = new byte[NTP_PACKET_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(response, response.length);
            socket.receive(responsePacket);

            return extractTransmitTimeMs(responsePacket.getData());
        }
    }

    private long extractTransmitTimeMs(byte[] ntpData) {
        long seconds = readUnsignedInt(ntpData, 40);
        long fraction = readUnsignedInt(ntpData, 44);

        long unixSecondsMs = (seconds * 1000L) - NTP_TO_UNIX_EPOCH_MS;
        long fractionMs = (fraction * 1000L) / 0x1_0000_0000L;
        return unixSecondsMs + fractionMs;
    }

    private long readUnsignedInt(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24)
                | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8)
                | (data[offset + 3] & 0xFFL);
    }
}
