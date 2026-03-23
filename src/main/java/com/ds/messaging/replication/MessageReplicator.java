package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Per-message replication helper with ACK tracking and retry support.
 */
public class MessageReplicator {
    private static final Logger logger = Logger.getInstance();

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 50L;

    private final Map<String, CompletableFuture<Boolean>> ackStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAttempt = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> failedNodes = new ConcurrentHashMap<>();
    private final Map<String, Message> messageStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ServerNode>> nodeRegistry = new ConcurrentHashMap<>();

    public void replicate(Message msg, ServerNode target) {
        if (msg == null || target == null) {
            return;
        }

        String messageId = msg.getMessageId();
        String nodeId = target.getNodeId();
        String ackKey = ackKey(messageId, nodeId);

        messageStore.put(messageId, msg);
        nodeRegistry.computeIfAbsent(messageId, k -> new ConcurrentHashMap<>()).put(nodeId, target);
        ackStates.putIfAbsent(messageId, new CompletableFuture<>());
        ackStates.putIfAbsent(ackKey, new CompletableFuture<>());

        attempts.merge(messageId, 1, Integer::sum);
        lastAttempt.put(messageId, System.currentTimeMillis());

        try {
            if (!target.isHealthy()) {
                onReplicationFail(messageId, nodeId);
                return;
            }

            // Current project has no real network transport yet; treat enqueue on target as durable write ACK.
            target.handleIncomingMessage(msg);
            acknowledge(messageId, nodeId);
        } catch (Exception ex) {
            logger.warn("Replication failed for message {} to node {}", messageId, nodeId);
            onReplicationFail(messageId, nodeId);
        }
    }

    public boolean waitForAck(String messageId, int timeoutMs) {
        CompletableFuture<Boolean> ack = ackStates.computeIfAbsent(messageId, k -> new CompletableFuture<>());
        try {
            return ack.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            return false;
        }
    }

    public void retryReplication(String messageId) {
        Message msg = messageStore.get(messageId);
        if (msg == null) {
            return;
        }

        Set<String> failed = failedNodes.get(messageId);
        if (failed == null || failed.isEmpty()) {
            return;
        }

        int currentAttempts = attempts.getOrDefault(messageId, 0);
        while (currentAttempts < MAX_RETRIES && !failed.isEmpty()) {
            long backoffMs = (long) (BASE_BACKOFF_MS * Math.pow(2, currentAttempts));
            long jitter = ThreadLocalRandom.current().nextLong(10, 30);
            sleepQuietly(backoffMs + jitter);

            for (String nodeId : failed.toArray(new String[0])) {
                ServerNode node = nodeRegistry.getOrDefault(messageId, new ConcurrentHashMap<>()).get(nodeId);
                if (node != null) {
                    replicate(msg, node);
                    if (waitForAck(ackKey(messageId, nodeId), 500)) {
                        failed.remove(nodeId);
                    }
                }
            }
            currentAttempts = attempts.getOrDefault(messageId, 0);
        }
    }

    public void onReplicationFail(String messageId, String nodeId) {
        if (messageId == null || nodeId == null) {
            return;
        }
        failedNodes.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet()).add(nodeId);
        ackStates.computeIfAbsent(ackKey(messageId, nodeId), k -> new CompletableFuture<>()).complete(false);
    }

    public void acknowledge(String messageId, String nodeId) {
        CompletableFuture<Boolean> success = CompletableFuture.completedFuture(true);
        ackStates.put(messageId, success);
        ackStates.put(ackKey(messageId, nodeId), success);
        Set<String> failed = failedNodes.get(messageId);
        if (failed != null) {
            failed.remove(nodeId);
        }
    }

    public int getAttemptCount(String messageId) {
        return attempts.getOrDefault(messageId, 0);
    }

    public Set<String> getFailedNodes(String messageId) {
        return failedNodes.getOrDefault(messageId, ConcurrentHashMap.newKeySet());
    }

    static String ackKey(String messageId, String nodeId) {
        return messageId + "@" + nodeId;
    }

    private void sleepQuietly(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
