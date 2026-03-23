package com.ds.messaging.replication;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe message deduplication with fixed-size LRU retention.
 */
public class MessageDeduplicator {
    private static final int DEFAULT_CAPACITY = 10_000;
    private static final long DEFAULT_RETENTION_MS = 60 * 60 * 1000L;

    private final int maxEntries;
    private final Object lock = new Object();

    private final LinkedHashMap<String, Long> seenMessageIds;

    public MessageDeduplicator() {
        this(DEFAULT_CAPACITY);
    }

    public MessageDeduplicator(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.seenMessageIds = new LinkedHashMap<String, Long>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MessageDeduplicator.this.maxEntries;
            }
        };
    }

    public boolean isDuplicate(String messageId) {
        if (messageId == null) {
            return false;
        }
        synchronized (lock) {
            return seenMessageIds.containsKey(messageId);
        }
    }

    public void addMessageId(String messageId) {
        if (messageId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (lock) {
            seenMessageIds.put(messageId, now);
        }
    }

    public void removeOldMessages(long retentionMs) {
        long effectiveRetention = retentionMs > 0 ? retentionMs : DEFAULT_RETENTION_MS;
        long cutoff = System.currentTimeMillis() - effectiveRetention;

        synchronized (lock) {
            Iterator<Map.Entry<String, Long>> iterator = seenMessageIds.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue() < cutoff) {
                    iterator.remove();
                }
            }
        }
    }

    public void clearCache() {
        synchronized (lock) {
            seenMessageIds.clear();
        }
    }

    int size() {
        synchronized (lock) {
            return seenMessageIds.size();
        }
    }
}
