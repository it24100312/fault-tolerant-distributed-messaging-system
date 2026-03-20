package com.ds.messaging.client;

import com.google.gson.Gson;
import java.util.*;

/**
 * Represents a message in the distributed messaging system.
 * 
 * Responsibilities:
 * - Store message content and metadata
 * - Support serialization/deserialization (JSON)
 * - Track message ID, sender, timestamp
 * - Support ordering (logical clock, vector clock)
 * 
 * TODO: Implement serialization and all methods
 */
public class Message {
    private static final Gson gson = new Gson();
    
    private String messageId;
    private String senderId;
    private String content;
    private long physicalTimestamp;  // Real world time
    private long logicalClock;       // Lamport/Logical clock
    private int[] vectorClock;       // Vector clock for causality
    
    /**
     * Create a new message
     */
    public Message(String senderId, String content) {
        // TODO: Implement initialization
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.content = content;
        this.physicalTimestamp = System.currentTimeMillis();
        this.logicalClock = 0;
        this.vectorClock = new int[0];
    }
    
    /**
     * Convert message to JSON string
     */
    public String toJSON() {
        // TODO: Implement JSON serialization
        // Use Gson to convert this object to JSON
        return gson.toJson(this);
    }
    
    /**
     * Create message from JSON string
     */
    public static Message fromJSON(String json) {
        // TODO: Implement JSON deserialization
        // Use Gson to convert JSON back to Message object
        return gson.fromJson(json, Message.class);
    }
    
    /**
     * Check message equality
     */
    @Override
    public boolean equals(Object obj) {
        // TODO: Implement equality check
        if (!(obj instanceof Message)) return false;
        Message other = (Message) obj;
        return this.messageId.equals(other.messageId);
    }
    
    /**
     * Get message hash
     */
    @Override
    public int hashCode() {
        // TODO: Implement hash code
        return messageId.hashCode();
    }
    
    /**
     * String representation
     */
    @Override
    public String toString() {
        // TODO: Implement toString
        return String.format("Message[id=%s, from=%s, content=%s, lc=%d]",
                messageId, senderId, content, logicalClock);
    }
    
    // Getters and Setters
    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public long getPhysicalTimestamp() { return physicalTimestamp; }
    public long getLogicalClock() { return logicalClock; }
    public void setLogicalClock(long clock) { this.logicalClock = clock; }
    public int[] getVectorClock() { return vectorClock; }
    public void setVectorClock(int[] clock) { this.vectorClock = clock; }
}
