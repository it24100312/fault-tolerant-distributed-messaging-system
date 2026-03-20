package com.ds.messaging.utils;

import java.io.*;
import java.util.*;

/**
 * System configuration management.
 * 
 * TODO: Implement configuration loading and management
 */
public class Config {
    private Properties properties;
    
    // Default configuration values
    private static final int DEFAULT_PORT = 9999;
    private static final int DEFAULT_NODE_COUNT = 3;
    private static final long DEFAULT_HEARTBEAT_INTERVAL = 2000;  // ms
    private static final long DEFAULT_ELECTION_TIMEOUT = 5000;    // ms
    private static final int DEFAULT_REPLICATION_FACTOR = 2;
    
    /**
     * Create default configuration
     */
    public Config() {
        // TODO: Implement initialization
        this.properties = new Properties();
        setDefaults();
    }
    
    /**
     * Load configuration from file
     */
    public void loadFromFile(String filepath) throws IOException {
        // TODO: Implement file loading
        // 1. Open file
        // 2. Load properties
        // 3. Parse values
        try (FileReader reader = new FileReader(filepath)) {
            properties.load(reader);
            Logger.getInstance().info("Configuration loaded from: {}", filepath);
        }
    }
    
    /**
     * Save configuration to file
     */
    public void saveToFile(String filepath) throws IOException {
        // TODO: Implement file saving
        try (FileWriter writer = new FileWriter(filepath)) {
            properties.store(writer, "Distributed Messaging System Configuration");
            Logger.getInstance().info("Configuration saved to: {}", filepath);
        }
    }
    
    /**
     * Set default configuration values
     */
    private void setDefaults() {
        // TODO: Implement defaults
        properties.setProperty("server.port", String.valueOf(DEFAULT_PORT));
        properties.setProperty("cluster.nodeCount", String.valueOf(DEFAULT_NODE_COUNT));
        properties.setProperty("heartbeat.intervalMs", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL));
        properties.setProperty("election.timeoutMs", String.valueOf(DEFAULT_ELECTION_TIMEOUT));
        properties.setProperty("replication.factor", String.valueOf(DEFAULT_REPLICATION_FACTOR));
    }
    
    // Getter methods
    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", String.valueOf(DEFAULT_PORT)));
    }
    
    public int getNodeCount() {
        return Integer.parseInt(properties.getProperty("cluster.nodeCount", String.valueOf(DEFAULT_NODE_COUNT)));
    }
    
    public long getHeartbeatInterval() {
        return Long.parseLong(properties.getProperty("heartbeat.intervalMs", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL)));
    }
    
    public long getElectionTimeout() {
        return Long.parseLong(properties.getProperty("election.timeoutMs", String.valueOf(DEFAULT_ELECTION_TIMEOUT)));
    }
    
    public int getReplicationFactor() {
        return Integer.parseInt(properties.getProperty("replication.factor", String.valueOf(DEFAULT_REPLICATION_FACTOR)));
    }
    
    // Setter methods
    public void setServerPort(int port) {
        properties.setProperty("server.port", String.valueOf(port));
    }
    
    public void setNodeCount(int count) {
        properties.setProperty("cluster.nodeCount", String.valueOf(count));
    }
    
    public void setHeartbeatInterval(long ms) {
        properties.setProperty("heartbeat.intervalMs", String.valueOf(ms));
    }
    
    public void setElectionTimeout(long ms) {
        properties.setProperty("election.timeoutMs", String.valueOf(ms));
    }
    
    public void setReplicationFactor(int factor) {
        properties.setProperty("replication.factor", String.valueOf(factor));
    }
    
    /**
     * Get raw property value
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Print all configuration
     */
    public void print() {
        Logger.getInstance().info("=== Configuration ===");
        properties.forEach((key, value) -> 
            Logger.getInstance().info("{} = {}", key, value));
    }
}
