package com.ds.messaging.replication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

public class MessageDeduplicatorTest {

    @Test
    public void testDuplicateDetection() {
        MessageDeduplicator deduplicator = new MessageDeduplicator();
        String id = "m-1";

        Assert.assertFalse(deduplicator.isDuplicate(id));
        deduplicator.addMessageId(id);
        Assert.assertTrue(deduplicator.isDuplicate(id));
    }

    @Test
    public void testLruEviction() {
        MessageDeduplicator deduplicator = new MessageDeduplicator(3);

        deduplicator.addMessageId("m1");
        deduplicator.addMessageId("m2");
        deduplicator.addMessageId("m3");
        deduplicator.addMessageId("m4");

        Assert.assertFalse(deduplicator.isDuplicate("m1"));
        Assert.assertTrue(deduplicator.isDuplicate("m4"));
    }

    @Test
    public void testCleanupOldEntries() throws Exception {
        MessageDeduplicator deduplicator = new MessageDeduplicator(10);

        deduplicator.addMessageId("old");
        Thread.sleep(5);
        deduplicator.removeOldMessages(1);

        Assert.assertFalse(deduplicator.isDuplicate("old"));
    }

    @Test
    public void testConcurrentAccess() throws Exception {
        MessageDeduplicator deduplicator = new MessageDeduplicator(500);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int idx = i;
            pool.submit(() -> {
                deduplicator.addMessageId("msg-" + idx);
                deduplicator.isDuplicate("msg-" + idx);
                latch.countDown();
            });
        }

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertTrue(deduplicator.size() <= 500);
        pool.shutdownNow();
    }
}
