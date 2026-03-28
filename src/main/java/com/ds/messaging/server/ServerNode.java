package com.ds.messaging.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ds.messaging.client.Message;
import com.ds.messaging.utils.Logger;

/**
 * Represents a single node in the distributed messaging system.
 * 
 * Responsibilities:
 * - Maintain node state and identity
 * - Send and receive messages
 * - Track connection status
 * - Process incoming messages
 * 
 * TODO: Implement all methods
 */
public class ServerNode {
    private static final Logger logger = Logger.getInstance();

    public static class StoredMessage {
        private final String messageId;
        private final String senderId;
        private final String content;
        private final long storedAt;
        private final String storageType;

        public StoredMessage(String messageId, String senderId, String content, long storedAt, String storageType) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.content = content;
            this.storedAt = storedAt;
            this.storageType = storageType;
        }

        public String getMessageId() { return messageId; }
        public String getSenderId() { return senderId; }
        public String getContent() { return content; }
        public long getStoredAt() { return storedAt; }
        public String getStorageType() { return storageType; }
    }
    
    private final String nodeId;
    private final String host;
    private final int port;
    private NodeState state;
    
    private final Queue<Message> inboundQueue;
    private final Queue<Message> outboundQueue;
    private final Map<String, ServerNode> peers;
    private final List<StoredMessage> storedMessages;
    private volatile long lastHeartbeat;
    private volatile boolean listenerRunning;
    
    /**
     * Create a new server node
     */
    public ServerNode(String nodeId, String host, int port) {
        // TODO: Implement initialization
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.state = NodeState.STARTING;
        this.inboundQueue = new ConcurrentLinkedQueue<>();
        this.outboundQueue = new ConcurrentLinkedQueue<>();
        this.peers = new ConcurrentHashMap<>();
        this.storedMessages = new CopyOnWriteArrayList<>();
        this.lastHeartbeat = System.currentTimeMillis();
        this.listenerRunning = false;
    }
    
    /**
     * Initialize node resources
     */
    public void initialize() {
        // TODO: Implement initialization
        // 1. Setup network listeners
        // 2. Initialize message processors
        // 3. Change state to READY
        state = NodeState.READY;
        recordHeartbeat();
        logger.info("Node {} initialized", nodeId);
    }
    
    /**
     * Connect to peer nodes
     */
    public void connect() {
        // TODO: Implement connection logic
        // 1. Establish connections to other nodes
        // 2. Send handshake messages
        // 3. Update connection status
        logger.info("Node {} connecting...", nodeId);
    }

    /**
     * Start listening for network messages.
     */
    public void startNetworkListener() throws IOException {
        listenerRunning = true;
        logger.info("Node {} listener started on {}:{}", nodeId, host, port);
    }

    public void stopNetworkListener() {
        listenerRunning = false;
        logger.info("Node {} listener stopped", nodeId);
    }

    public boolean isListenerRunning() {
        return listenerRunning;
    }

    public void registerPeer(ServerNode peer) {
        if (peer == null) {
            return;
        }
        peers.put(peer.getNodeId(), peer);
    }

    public void clearPeers() {
        peers.clear();
    }

    public Collection<ServerNode> getPeers() {
        return new ArrayList<>(peers.values());
    }
    
    /**
     * Send message to target node
     */
    public void sendToNode(String targetId, Message msg) {
        // TODO: Implement message sending
        // 1. Look up target node
        // 2. Add message to outbound queue
        // 3. Actually send over network
        // 4. Track if delivery succeeded
        outboundQueue.offer(msg);
        ServerNode targetNode = peers.get(targetId);
        if (targetNode == null) {
            logger.warn("Target node {} not found from {}", targetId, nodeId);
            return;
        }

        if (targetNode.getState() == NodeState.DEAD || !targetNode.isListenerRunning()) {
            logger.warn("Target node {} is not reachable", targetId);
            return;
        }

        targetNode.handleIncomingMessage(msg);
        logger.info("Delivered message {} from {} to {}", msg.getMessageId(), nodeId, targetId);
    }
    
    /**
     * Receive message from inbound queue
     */
    public Message receiveMessage() {
        // TODO: Implement message receiving
        return inboundQueue.poll();
    }
    
    /**
     * Process a message received from peer
     */
    public void handleIncomingMessage(Message msg) {
        // TODO: Implement incoming message handling
        // 1. Validate message
        // 2. Deserialize if needed
        // 3. Add to inbound queue
        // 4. Trigger message processing
        inboundQueue.offer(msg);
        storeMessage(msg, "direct");
        recordHeartbeat();
        logger.info("Node {} received message from {}", nodeId, msg.getSenderId());
    }

    public void storeReplica(Message msg) {
        storeMessage(msg, "replica");
        logger.debug("Node {} stored replica for message {}", nodeId, msg.getMessageId());
    }

    private void storeMessage(Message msg, String storageType) {
        if (msg == null) {
            return;
        }
        storedMessages.add(new StoredMessage(
                msg.getMessageId(),
                msg.getSenderId(),
                msg.getContent(),
                System.currentTimeMillis(),
                storageType));
    }
    
    /**
     * Check if node is healthy
     */
    public boolean isHealthy() {
        // Node is healthy if:
        // 1. State is READY or SYNCING
        // 2. Last heartbeat was within 6 seconds
        if (state != NodeState.READY && state != NodeState.SYNCING) {
            return false;
        }
        long timeSinceHB = getTimeSinceLastHeartbeat();
        return timeSinceHB < 6000;  // 6 seconds threshold
    }
    
    /**
     * Update last heartbeat timestamp
     */
    public void recordHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    /**
     * Get time since last heartbeat
     */
    public long getTimeSinceLastHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeat;
    }
    
    /**
     * Gracefully shutdown node
     */
    public void shutdown() {
        // TODO: Implement shutdown
        // 1. Process remaining messages
        // 2. Close network connections
        // 3. Clean up threads
        // 4. Change state to SHUTTING_DOWN then DEAD
        state = NodeState.SHUTTING_DOWN;
        stopNetworkListener();
        clearPeers();
        state = NodeState.DEAD;
        logger.info("Node {} shutting down", nodeId);
    }
    
    // Getters
    public String getNodeId() { return nodeId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public NodeState getState() { return state; }
    public void setState(NodeState newState) { this.state = newState; }
    public int getInboundQueueSize() { return inboundQueue.size(); }
    public int getOutboundQueueSize() { return outboundQueue.size(); }
    public int getStoredMessageCount() { return storedMessages.size(); }
    public List<StoredMessage> getStoredMessages() { return Collections.unmodifiableList(storedMessages); }

    public int getDirectStoredCount() {
        int count = 0;
        for (StoredMessage m : storedMessages) {
            if ("direct".equals(m.getStorageType())) {
                count++;
            }
        }
        return count;
    }

    public int getReplicaStoredCount() {
        int count = 0;
        for (StoredMessage m : storedMessages) {
            if ("replica".equals(m.getStorageType())) {
                count++;
            }
        }
        return count;
    }

    public List<StoredMessage> getRecentStoredMessages(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        int size = storedMessages.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        List<StoredMessage> out = new ArrayList<>();
        for (int i = size - 1; i >= 0 && out.size() < limit; i--) {
            out.add(storedMessages.get(i));
        }
        return out;
    }
}
