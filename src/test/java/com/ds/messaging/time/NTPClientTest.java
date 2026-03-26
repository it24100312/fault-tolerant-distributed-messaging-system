package com.ds.messaging.time;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public class NTPClientTest {

    @Test
    public void testSuccessfulSyncUpdatesOffsetAndStatus() {
        NTPClient client = new StubNTPClient(120L, false);

        client.syncWithNTP("pool.ntp.org");

        assertTrue("Client should be marked synced after successful sync", client.isTimeSynced());
        assertTrue("Offset should reflect NTP skew", Math.abs(client.getSystemTimeOffsetMs() - 120L) < 300L);
        assertTrue("Last sync timestamp should be set", client.getLastSyncEpochMs() > 0L);
    }

    @Test
    public void testBlankServerMarksUnsynced() {
        NTPClient client = new StubNTPClient(50L, false);

        client.syncWithNTP("   ");

        assertFalse("Blank server input should mark client unsynced", client.isTimeSynced());
    }

    @Test
    public void testFailedSyncMarksUnsynced() {
        NTPClient client = new StubNTPClient(0L, true);

        client.syncWithNTP("pool.ntp.org");

        assertFalse("Failed sync should mark client unsynced", client.isTimeSynced());
    }

    private static class StubNTPClient extends NTPClient {
        private final long simulatedOffsetMs;
        private final boolean fail;

        private StubNTPClient(long simulatedOffsetMs, boolean fail) {
            this.simulatedOffsetMs = simulatedOffsetMs;
            this.fail = fail;
        }

        @Override
        protected long queryNtpTimeMillis(String ntpServer, int timeoutMs) throws IOException {
            if (fail) {
                throw new IOException("simulated ntp failure");
            }
            return System.currentTimeMillis() + simulatedOffsetMs;
        }
    }
}
