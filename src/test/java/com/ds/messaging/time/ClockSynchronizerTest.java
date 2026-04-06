package com.ds.messaging.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ds.messaging.server.FailureDetector;
import com.ds.messaging.server.LeaderElection;
import com.ds.messaging.server.ServerNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClockSynchronizerTest {

    private FailureDetector failureDetector;
    private TestClockSynchronizer synchronizer;

    @Before
    public void setUp() {
        failureDetector = new FailureDetector(new LeaderElection());
        synchronizer = new TestClockSynchronizer(new NTPClient(), failureDetector);
    }

    @After
    public void tearDown() {
        synchronizer.shutdown();
        failureDetector.shutdown();
    }

    @Test
    public void testSynchronizeClocksComputesAverageCorrection() {
        ServerNode nodeA = readyNode("A");
        ServerNode nodeB = readyNode("B");

        synchronizer.setLocalBaseTime(1000L);
        synchronizer.setNodeTime("A", 1010L);
        synchronizer.setNodeTime("B", 980L);

        synchronizer.synchronizeClocks(Arrays.asList(nodeA, nodeB));

        assertEquals("Local correction should move local time to cluster average", -4L, synchronizer.getLocalTimeOffset());
        assertEquals("Node A correction should move toward average", -14L, synchronizer.getNodeCorrectionMs("A"));
        assertEquals("Node B correction should move toward average", 16L, synchronizer.getNodeCorrectionMs("B"));
        assertTrue("Last sync timestamp should be set", synchronizer.getLastSyncEpochMs() > 0L);
    }

    @Test
    public void testSynchronizeSkipsFailedNodes() {
        ServerNode nodeA = readyNode("A");
        ServerNode nodeB = readyNode("B");

        failureDetector.startHeartbeat(nodeB);
        failureDetector.onHeartbeatMissed("B");
        failureDetector.onHeartbeatMissed("B");
        failureDetector.onHeartbeatMissed("B");

        synchronizer.setLocalBaseTime(1000L);
        synchronizer.setNodeTime("A", 1020L);
        synchronizer.setNodeTime("B", 2000L);

        synchronizer.synchronizeClocks(Arrays.asList(nodeA, nodeB));

        assertEquals("Failed node should not affect average", 10L, synchronizer.getLocalTimeOffset());
        assertEquals("Healthy node should receive correction", -10L, synchronizer.getNodeCorrectionMs("A"));
        assertEquals("Failed node correction should not be tracked", 0L, synchronizer.getNodeCorrectionMs("B"));
    }

    @Test
    public void testPeriodicSyncStarts() {
        synchronizer.periodicSync();
        assertTrue("Periodic sync flag should be enabled", synchronizer.isPeriodicSyncRunning());
    }

    @Test
    public void testSyncReducesClockSkew() {
        ServerNode nodeA = readyNode("A");
        ServerNode nodeB = readyNode("B");

        synchronizer.setLocalBaseTime(1000L);
        synchronizer.setNodeTime("A", 1600L);
        synchronizer.setNodeTime("B", 700L);

        List<Long> beforeTimes = Arrays.asList(1000L, 1600L, 700L);
        long beforeSkew = maxSkew(beforeTimes);

        synchronizer.synchronizeClocks(Arrays.asList(nodeA, nodeB));

        long localAfter = 1000L + synchronizer.getLocalTimeOffset();
        long nodeAAfter = 1600L + synchronizer.getNodeCorrectionMs("A");
        long nodeBAfter = 700L + synchronizer.getNodeCorrectionMs("B");

        List<Long> afterTimes = Arrays.asList(localAfter, nodeAAfter, nodeBAfter);
        long afterSkew = maxSkew(afterTimes);

        assertTrue("Clock skew should reduce after synchronization", afterSkew < beforeSkew);
        assertEquals("All participants should converge to same cluster time", 0L, afterSkew);
    }

    @Test
    public void testConcurrentSynchronizeCallsDoNotDeadlock() throws Exception {
        ServerNode nodeA = readyNode("A");
        ServerNode nodeB = readyNode("B");

        synchronizer.setLocalBaseTime(1000L);
        synchronizer.setNodeTime("A", 1100L);
        synchronizer.setNodeTime("B", 900L);

        int totalTasks = 80;
        CountDownLatch latch = new CountDownLatch(totalTasks);
        ExecutorService pool = Executors.newFixedThreadPool(8);

        for (int i = 0; i < totalTasks; i++) {
            pool.submit(() -> {
                synchronizer.synchronizeClocks(Arrays.asList(nodeA, nodeB));
                latch.countDown();
            });
        }

        assertTrue("Concurrent synchronize calls should complete without deadlock",
                latch.await(3, TimeUnit.SECONDS));

        pool.shutdownNow();
    }

    private ServerNode readyNode(String id) {
        ServerNode node = new ServerNode(id, "localhost", 5000);
        node.initialize();
        return node;
    }

    private long maxSkew(List<Long> times) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (long value : times) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return max - min;
    }

    private static class TestClockSynchronizer extends ClockSynchronizer {
        private final Map<String, Long> nodeTimes = new ConcurrentHashMap<>();
        private volatile long localBaseTime;

        TestClockSynchronizer(NTPClient ntpClient, FailureDetector failureDetector) {
            super(ntpClient, failureDetector);
        }

        void setNodeTime(String nodeId, long value) {
            nodeTimes.put(nodeId, value);
        }

        void setLocalBaseTime(long value) {
            localBaseTime = value;
        }

        @Override
        protected long resolveLocalBaseTime() {
            return localBaseTime;
        }

        @Override
        protected long resolveNodeTime(ServerNode node) {
            return nodeTimes.getOrDefault(node.getNodeId(), localBaseTime);
        }
    }
}
