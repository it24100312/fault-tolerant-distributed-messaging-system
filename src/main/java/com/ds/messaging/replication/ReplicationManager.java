package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.FailureDetector;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates quorum-based message replication and replication status tracking.
 */
public class ReplicationManager {
    private static final Logger logger = Logger.getInstance();

    private static final long DEFAULT_REPLICATION_TIMEOUT_MS = 5000L;
    private static final int DEFAULT_ACK_TIMEOUT_MS = 1000;

    private final FailureDetector failureDetector;
    private final MessageReplicator messageReplicator;
    private final MessageDeduplicator deduplicator;
    private final ConsistencyHandler consistencyHandler;
    private final OrderingPort orderingPort;
    private final ConsensusPort consensusPort;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // messageId -> acked nodeIds
    private final ConcurrentHashMap<String, Set<String>> replicationTracker = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> quorumByMessage = new ConcurrentHashMap<>();
    private final Set<String> committedMessages = ConcurrentHashMap.newKeySet();

    private volatile long replicationTimeoutMs = DEFAULT_REPLICATION_TIMEOUT_MS;

    public ReplicationManager(FailureDetector failureDetector) {
        this(failureDetector, new MessageReplicator(), new MessageDeduplicator(), null, null);
    }

    public ReplicationManager(
            FailureDetector failureDetector,
            MessageReplicator messageReplicator,
            MessageDeduplicator deduplicator,
            OrderingPort orderingPort,
            ConsensusPort consensusPort) {
        this.failureDetector = failureDetector;
        this.messageReplicator = messageReplicator;
        this.deduplicator = deduplicator;
        this.orderingPort = orderingPort;
        this.consensusPort = consensusPort;
        this.consistencyHandler = new ConsistencyHandler(this);
    }

    public void replicateMessage(Message msg, List<ServerNode> replicas) {
        if (msg == null || replicas == null || replicas.isEmpty()) {
            return;
        }

        String messageId = msg.getMessageId();
        if (deduplicator.isDuplicate(messageId)) {
            logger.debug("Skipping duplicate message {}", messageId);
            return;
        }

        if (orderingPort != null) {
            orderingPort.assignTimestamp(msg);
        }

        List<ServerNode> healthyReplicas = selectHealthyReplicas(replicas);
        int quorumSize = calculateQuorum(replicas.size());
        quorumByMessage.put(messageId, quorumSize);

        if (healthyReplicas.size() < quorumSize) {
            logger.warn("Replication failed fast for {}: healthy nodes {}, required quorum {}",
                    messageId, healthyReplicas.size(), quorumSize);
            return;
        }

        replicationTracker.putIfAbsent(messageId, ConcurrentHashMap.newKeySet());
        CountDownLatch quorumLatch = new CountDownLatch(quorumSize);

        for (ServerNode node : healthyReplicas) {
            executor.submit(() -> {
                String nodeId = node.getNodeId();
                String ackKey = MessageReplicator.ackKey(messageId, nodeId);

                messageReplicator.replicate(msg, node);
                if (messageReplicator.waitForAck(ackKey, DEFAULT_ACK_TIMEOUT_MS)) {
                    boolean isNewAck = confirmReplicationInternal(messageId, nodeId);
                    if (isNewAck) {
                        consistencyHandler.recordReplicaMessage(messageId, nodeId, msg);
                        quorumLatch.countDown();
                    }
                } else {
                    messageReplicator.onReplicationFail(messageId, nodeId);
                }
            });
        }

        boolean quorumReached = false;
        try {
            quorumReached = quorumLatch.await(replicationTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (quorumReached) {
            committedMessages.add(messageId);
            deduplicator.addMessageId(messageId);
            if (consensusPort != null) {
                consensusPort.onMessageReplicated(messageId);
            }
        } else {
            logger.warn("Quorum not reached for message {}. Triggering retry.", messageId);
            messageReplicator.retryReplication(messageId);
        }
    }

    public void confirmReplication(String messageId, String nodeId) {
        confirmReplicationInternal(messageId, nodeId);
    }

    public boolean isMessageReplicated(String messageId) {
        if (messageId == null) {
            return false;
        }
        if (committedMessages.contains(messageId)) {
            return true;
        }

        int quorum = quorumByMessage.getOrDefault(messageId, Integer.MAX_VALUE);
        return getReplicationCount(messageId) >= quorum;
    }

    public int getReplicationCount(String messageId) {
        return replicationTracker.getOrDefault(messageId, Collections.emptySet()).size();
    }

    public List<ServerNode> selectHealthyReplicas(List<ServerNode> allReplicas) {
        if (allReplicas == null || allReplicas.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> failedNodes = failureDetector == null ? Collections.emptyList() : failureDetector.getFailedNodes();
        List<ServerNode> healthy = new ArrayList<>();

        for (ServerNode node : allReplicas) {
            if (node != null && node.isHealthy() && !failedNodes.contains(node.getNodeId())) {
                healthy.add(node);
            }
        }
        return healthy;
    }

    public static int calculateQuorum(int nodeCount) {
        if (nodeCount <= 0) {
            return 0;
        }
        return (nodeCount / 2) + 1;
    }

    public ConsistencyHandler getConsistencyHandler() {
        return consistencyHandler;
    }

    public void setReplicationTimeoutMs(long replicationTimeoutMs) {
        this.replicationTimeoutMs = Math.max(100L, replicationTimeoutMs);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private boolean confirmReplicationInternal(String messageId, String nodeId) {
        if (messageId == null || nodeId == null) {
            return false;
        }
        Set<String> ackedNodes = replicationTracker.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet());
        return ackedNodes.add(nodeId);
    }
}
