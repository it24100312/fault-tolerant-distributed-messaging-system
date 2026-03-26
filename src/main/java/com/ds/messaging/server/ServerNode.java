package com.ds.messaging.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    
    private final String nodeId;
    private final String host;
    private final int port;
    private NodeState state;
    
    private final Queue<Message> inboundQueue;
    private final Queue<Message> outboundQueue;
    private volatile long lastHeartbeat;
    
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
        this.lastHeartbeat = System.currentTimeMillis();
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
     * Send message to target node
     */
    public void sendToNode(String targetId, Message msg) {
        // TODO: Implement message sending
        // 1. Look up target node
        // 2. Add message to outbound queue
        // 3. Actually send over network
        // 4. Track if delivery succeeded
        outboundQueue.offer(msg);
        logger.debug("Message queued for {}", targetId);
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
        logger.info("Node {} shutting down", nodeId);
    }
    
    // Getters
    public String getNodeId() { return nodeId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public NodeState getState() { return state; }
    public void setState(NodeState newState) { this.state = newState; }
}
