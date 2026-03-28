package com.ds.messaging.utils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * System configuration management.
 * 
 * TODO: Implement configuration loading and management
 */
public class Config {
    private final Properties properties;

    public static class NodeEndpoint {
        private final String nodeId;
        private final String host;
        private final int port;

        public NodeEndpoint(String nodeId, String host, int port) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }
    
    // Default configuration values
    private static final int DEFAULT_PORT = 9999;
    private static final int DEFAULT_NODE_COUNT = 3;
    private static final long DEFAULT_HEARTBEAT_INTERVAL = 2000;  // ms
    private static final long DEFAULT_ELECTION_TIMEOUT = 5000;    // ms
    private static final int DEFAULT_REPLICATION_FACTOR = 2;
    private static final boolean DEFAULT_UI_ENABLED = true;
    private static final int DEFAULT_UI_PORT = 8080;
    
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
        properties.setProperty("cluster.nodes", buildDefaultClusterNodes());
        properties.setProperty("heartbeat.intervalMs", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL));
        properties.setProperty("election.timeoutMs", String.valueOf(DEFAULT_ELECTION_TIMEOUT));
        properties.setProperty("replication.factor", String.valueOf(DEFAULT_REPLICATION_FACTOR));
        properties.setProperty("ui.enabled", String.valueOf(DEFAULT_UI_ENABLED));
        properties.setProperty("ui.port", String.valueOf(DEFAULT_UI_PORT));
    }

    /**
     * Returns cluster nodes parsed from "cluster.nodes".
     *
     * Expected format:
     * node1:localhost:6001,node2:localhost:6002,node3:localhost:6003
     */
    public List<NodeEndpoint> getClusterNodes() {
        String raw = properties.getProperty("cluster.nodes", "").trim();
        if (raw.isEmpty()) {
            raw = buildDefaultClusterNodes();
        }

        List<NodeEndpoint> endpoints = new ArrayList<>();
        String[] entries = raw.split(",");
        for (String entry : entries) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 3) {
                continue;
            }

            String nodeId = parts[0].trim();
            String host = parts[1].trim();
            try {
                int port = Integer.parseInt(parts[2].trim());
                endpoints.add(new NodeEndpoint(nodeId, host, port));
            } catch (NumberFormatException ignored) {
                // Skip malformed endpoints.
            }
        }

        if (endpoints.isEmpty()) {
            String host = properties.getProperty("server.host", "localhost");
            int basePort = getServerPort();
            for (int i = 1; i <= getNodeCount(); i++) {
                endpoints.add(new NodeEndpoint("node" + i, host, basePort + (i - 1)));
            }
        }

        return endpoints;
    }

    public void setClusterNodes(List<NodeEndpoint> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            properties.setProperty("cluster.nodes", buildDefaultClusterNodes());
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            NodeEndpoint node = nodes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(node.getNodeId())
              .append(':')
              .append(node.getHost())
              .append(':')
              .append(node.getPort());
        }
        properties.setProperty("cluster.nodes", sb.toString());
    }

    private String buildDefaultClusterNodes() {
        int basePort = getServerPort();
        String host = properties.getProperty("server.host", "localhost");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= DEFAULT_NODE_COUNT; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append("node")
              .append(i)
              .append(':')
              .append(host)
              .append(':')
              .append(basePort + (i - 1));
        }
        return sb.toString();
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

    public boolean isUiEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.enabled", String.valueOf(DEFAULT_UI_ENABLED)));
    }

    public int getUiPort() {
        return Integer.parseInt(properties.getProperty("ui.port", String.valueOf(DEFAULT_UI_PORT)));
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

    public void setUiEnabled(boolean enabled) {
        properties.setProperty("ui.enabled", String.valueOf(enabled));
    }

    public void setUiPort(int port) {
        properties.setProperty("ui.port", String.valueOf(port));
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
