package com.ds.messaging.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ds.messaging.client.Message;
import com.ds.messaging.utils.Config;
import com.ds.messaging.utils.Config.NodeEndpoint;
import com.ds.messaging.utils.Logger;

/**
 * Main messaging server that manages a cluster of nodes.
 */
public class MessagingServer {
    private static final Logger logger = Logger.getInstance();
    private static final int MAX_EVENTS = 500;
    private static final int MAX_MESSAGE_LEDGER = 500;

    public static class ClusterEvent {
        private final long timestamp;
        private final String type;
        private final String details;

        public ClusterEvent(long timestamp, String type, String details) {
            this.timestamp = timestamp;
            this.type = type;
            this.details = details;
        }

        public long getTimestamp() { return timestamp; }
        public String getType() { return type; }
        public String getDetails() { return details; }
    }

    public static class NodeStatus {
        private final String nodeId;
        private final String host;
        private final int port;
        private final String state;
        private final boolean listenerRunning;
        private final boolean alive;
        private final int inboundQueue;
        private final int outboundQueue;
        private final int storedMessages;
        private final int directStoredMessages;
        private final int replicaStoredMessages;

        public NodeStatus(String nodeId, String host, int port, String state, boolean listenerRunning,
                          boolean alive, int inboundQueue, int outboundQueue, int storedMessages,
                          int directStoredMessages, int replicaStoredMessages) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.state = state;
            this.listenerRunning = listenerRunning;
            this.alive = alive;
            this.inboundQueue = inboundQueue;
            this.outboundQueue = outboundQueue;
            this.storedMessages = storedMessages;
            this.directStoredMessages = directStoredMessages;
            this.replicaStoredMessages = replicaStoredMessages;
        }

        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getState() { return state; }
        public boolean isListenerRunning() { return listenerRunning; }
        public boolean isAlive() { return alive; }
        public int getInboundQueue() { return inboundQueue; }
        public int getOutboundQueue() { return outboundQueue; }
        public int getStoredMessages() { return storedMessages; }
        public int getDirectStoredMessages() { return directStoredMessages; }
        public int getReplicaStoredMessages() { return replicaStoredMessages; }
    }

    public static class MessageRecord {
        private final String messageId;
        private final String sender;
        private final String content;
        private final String timestamp;
        private final long logicalTime;
        private final int replication;
        private final List<String> replicaLocations;
        private final String delivery;
        private final String ordering;
        private final String dedup;

        public MessageRecord(String messageId, String sender, String content, String timestamp,
                             long logicalTime, int replication, List<String> replicaLocations,
                             String delivery, String ordering, String dedup) {
            this.messageId = messageId;
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
            this.logicalTime = logicalTime;
            this.replication = replication;
            this.replicaLocations = replicaLocations;
            this.delivery = delivery;
            this.ordering = ordering;
            this.dedup = dedup;
        }

        public String getMessageId() { return messageId; }
        public String getSender() { return sender; }
        public String getContent() { return content; }
        public String getTimestamp() { return timestamp; }
        public long getLogicalTime() { return logicalTime; }
        public int getReplication() { return replication; }
        public List<String> getReplicaLocations() { return replicaLocations; }
        public String getDelivery() { return delivery; }
        public String getOrdering() { return ordering; }
        public String getDedup() { return dedup; }
    }

    private final Config config;
    private final Map<String, ServerNode> nodes;
    private final Map<String, NodeEndpoint> knownEndpoints;
    private final Deque<ClusterEvent> events;
    private final Deque<MessageRecord> messageLedger;
    private final Set<String> seenMessageContents;
    private long logicalClock;

    private ScheduledExecutorService executor;
    private boolean isRunning;
    private LeaderElection leaderElection;
    private FailureDetector failureDetector;

    public MessagingServer(Config config) {
        this.config = config;
        this.nodes = new ConcurrentHashMap<>();
        this.knownEndpoints = new ConcurrentHashMap<>();
        this.events = new LinkedList<>();
        this.messageLedger = new LinkedList<>();
        this.seenMessageContents = new HashSet<>();
        this.logicalClock = 0L;
        this.isRunning = false;
        logger.info("MessagingServer created with config");
        recordEvent("SERVER_CREATED", "Messaging server instance created");
    }

    public synchronized void start() {
        if (isRunning) {
            logger.warn("Server already running");
            return;
        }

        executor = Executors.newScheduledThreadPool(5);
        leaderElection = new LeaderElection();
        failureDetector = new FailureDetector(leaderElection);

        bootstrapNodesFromConfig();
        wireNodePeers();

        isRunning = true;

        for (ServerNode node : nodes.values()) {
            node.initialize();
            try {
                node.startNetworkListener();
            } catch (IOException e) {
                logger.error("Failed to start listener for {}", e);
                node.setState(NodeState.UNHEALTHY);
            }
            failureDetector.startHeartbeat(node);
        }

        electLeaderIfPossible();
        startLeaderReconciliationTask();

        logger.info("MessagingServer started with {} nodes", nodes.size());
        recordEvent("SERVER_STARTED", "Cluster started with " + nodes.size() + " nodes");
    }

    public synchronized void stop() {
        if (!isRunning) {
            logger.warn("Server not running");
            return;
        }

        isRunning = false;

        for (ServerNode node : nodes.values()) {
            node.shutdown();
        }

        if (failureDetector != null) {
            failureDetector.shutdown();
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        logger.info("MessagingServer stopped");
        recordEvent("SERVER_STOPPED", "Cluster shutdown completed");
    }

    public synchronized void addNode(String nodeId, String host, int port) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Node ID cannot be blank");
        }
        if (isPortInUse(port, nodeId)) {
            throw new IllegalArgumentException("Port already in use: " + port);
        }

        ServerNode node = new ServerNode(nodeId, host, port);
        nodes.put(nodeId, node);
        knownEndpoints.put(nodeId, new NodeEndpoint(nodeId, host, port));

        if (isRunning && failureDetector != null) {
            wireNodePeers();
            node.initialize();
            try {
                node.startNetworkListener();
            } catch (IOException e) {
                logger.error("Failed to start listener for {}", e);
            }
            failureDetector.startHeartbeat(node);
        }

        updateConfigFromNodes();
        electLeaderIfPossible();
        recordEvent("NODE_ADDED", "Node " + nodeId + " added at " + host + ":" + port);
    }

    public ServerNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<ServerNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public synchronized ServerNode getLeader() {
        if (leaderElection != null) {
            ServerNode current = leaderElection.getCurrentLeader();
            if (isRunning && (current == null || !current.isHealthy())) {
                electLeaderIfPossible();
            }
            return leaderElection.getCurrentLeader();
        }
        return null;
    }

    public synchronized void onNodeFailure(String nodeId) {
        logger.error("Node failed: {}", nodeId);
        recordEvent("NODE_FAILURE", "Node failure detected: " + nodeId);
        removeFailedNode(nodeId);
        notifyReplication(nodeId);
        notifyConsensus(nodeId);
    }

    public synchronized void removeFailedNode(String nodeId) {
        ServerNode node = nodes.remove(nodeId);
        if (node != null && failureDetector != null) {
            failureDetector.stopHeartbeat(nodeId);
            node.shutdown();
            logger.info("Removed failed node: {}", nodeId);
            wireNodePeers();
            updateConfigFromNodes();
            electLeaderIfPossible();
            recordEvent("NODE_REMOVED", "Node removed: " + nodeId);
        }
    }

    public synchronized boolean killNode(String nodeId) {
        ServerNode node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }

        ServerNode previousLeader = getLeader();
        boolean killedLeader = previousLeader != null && nodeId.equals(previousLeader.getNodeId());

        logger.warn("Operator requested kill for node: {}", nodeId);
        recordEvent("NODE_KILLED", "Operator killed node: " + nodeId);
        removeFailedNode(nodeId);
        if (killedLeader) {
            ServerNode newLeader = getLeader();
            String newLeaderId = newLeader == null ? "none" : newLeader.getNodeId();
            recordEvent("LEADER_FAILOVER", "Leader " + nodeId + " removed. New leader: " + newLeaderId + " (term " + getCurrentTerm() + ")");
        }
        return true;
    }

    public synchronized boolean recoverNode(String nodeId) {
        NodeEndpoint endpoint = knownEndpoints.get(nodeId);
        if (endpoint == null || nodes.containsKey(nodeId)) {
            return false;
        }

        logger.info("Recovering node {} at {}:{}", nodeId, endpoint.getHost(), endpoint.getPort());
        addNode(nodeId, endpoint.getHost(), endpoint.getPort());
        logger.info("Recovered node {}", nodeId);
        recordEvent("NODE_RECOVERED", "Node recovered: " + nodeId);
        return true;
    }

    public synchronized boolean recoverNode(String nodeId, String host, int port) {
        if (nodes.containsKey(nodeId)) {
            return false;
        }

        knownEndpoints.put(nodeId, new NodeEndpoint(nodeId, host, port));
        addNode(nodeId, host, port);
        logger.info("Recovered node {} at new endpoint {}:{}", nodeId, host, port);
        recordEvent("NODE_RECOVERED", "Node recovered with new endpoint: " + nodeId + " -> " + host + ":" + port);
        return true;
    }

    public synchronized boolean updateNodePort(String nodeId, int newPort) {
        NodeEndpoint endpoint = knownEndpoints.get(nodeId);
        if (endpoint == null || isPortInUse(newPort, nodeId)) {
            return false;
        }

        boolean wasRunning = nodes.containsKey(nodeId);
        if (wasRunning) {
            removeFailedNode(nodeId);
        }

        knownEndpoints.put(nodeId, new NodeEndpoint(nodeId, endpoint.getHost(), newPort));
        addNode(nodeId, endpoint.getHost(), newPort);
        logger.info("Updated node {} port to {}", nodeId, newPort);
        recordEvent("NODE_PORT_CHANGED", "Node " + nodeId + " moved to port " + newPort);
        return true;
    }

    public synchronized void resizeCluster(int desiredNodeCount) {
        if (desiredNodeCount <= 0) {
            throw new IllegalArgumentException("Node count must be > 0");
        }

        String host = config.getProperty("server.host");
        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        int basePort = config.getServerPort();
        for (int i = 1; i <= desiredNodeCount; i++) {
            String nodeId = "node" + i;
            if (!knownEndpoints.containsKey(nodeId)) {
                knownEndpoints.put(nodeId, new NodeEndpoint(nodeId, host, basePort + (i - 1)));
            }
            if (!nodes.containsKey(nodeId)) {
                NodeEndpoint endpoint = knownEndpoints.get(nodeId);
                addNode(nodeId, endpoint.getHost(), endpoint.getPort());
            }
        }

        List<String> currentIds = new ArrayList<>(nodes.keySet());
        for (String nodeId : currentIds) {
            Integer suffix = parseDefaultNodeSuffix(nodeId);
            if (suffix != null && suffix > desiredNodeCount) {
                removeFailedNode(nodeId);
            }
        }

        config.setNodeCount(desiredNodeCount);
        updateConfigFromNodes();
        logger.info("Cluster resized to {} nodes", desiredNodeCount);
        recordEvent("CLUSTER_RESIZED", "Cluster resized to " + desiredNodeCount + " nodes");
    }

    public synchronized boolean sendMessage(String fromNodeId, String toNodeId, String content) {
        ServerNode sender = nodes.get(fromNodeId);
        ServerNode receiver = nodes.get(toNodeId);
        if (sender == null || sender.getState() == NodeState.DEAD || receiver == null || receiver.getState() == NodeState.DEAD) {
            return false;
        }

        Message message = new Message(fromNodeId, content);
        logicalClock++;
        message.setLogicalClock(logicalClock);
        sender.sendToNode(toNodeId, message);

        int replicas = 0;
        List<String> replicaLocations = new ArrayList<>();
        replicaLocations.add(toNodeId + " \u2713");
        for (ServerNode node : nodes.values()) {
            if (node.getNodeId().equals(fromNodeId) || node.getNodeId().equals(toNodeId)) {
                continue;
            }
            if (node.getState() != NodeState.DEAD && node.isListenerRunning()) {
                node.storeReplica(message);
                replicas++;
                replicaLocations.add(node.getNodeId() + " \u2713");
            } else {
                replicaLocations.add(node.getNodeId() + " x");
            }
        }

        String dedup = seenMessageContents.add(content) ? "UNIQUE" : "DUPLICATE DETECTED";
        MessageRecord record = new MessageRecord(
                message.getMessageId(),
                fromNodeId,
                content,
                formatTime(System.currentTimeMillis()),
                logicalClock,
                replicas,
                replicaLocations,
                "DELIVERED",
                "ORDERED",
                dedup);
        messageLedger.addFirst(record);
        while (messageLedger.size() > MAX_MESSAGE_LEDGER) {
            messageLedger.removeLast();
        }

        logger.info("Message sent: {} -> {} | {}", fromNodeId, toNodeId, content);
        recordEvent("MESSAGE_SENT", "Message " + message.getMessageId() + " from " + fromNodeId + " to " + toNodeId + " with " + replicas + " replicas");
        return true;
    }

    public synchronized List<MessageRecord> getRecentMessages(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        List<MessageRecord> out = new ArrayList<>();
        int count = 0;
        for (MessageRecord m : messageLedger) {
            out.add(m);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return out;
    }

    public synchronized long getLogicalClock() {
        return logicalClock;
    }

    public synchronized List<String> getFailedNodeIds() {
        List<String> failed = new ArrayList<>();
        for (String nodeId : knownEndpoints.keySet()) {
            if (!nodes.containsKey(nodeId)) {
                failed.add(nodeId);
            }
        }
        failed.sort(String::compareTo);
        return failed;
    }

    public synchronized List<String> getActiveNodeIds() {
        List<String> active = new ArrayList<>(nodes.keySet());
        active.sort(String::compareTo);
        return active;
    }

    public synchronized int getRequiredQuorum() {
        int n = Math.max(1, nodes.size());
        return (n / 2) + 1;
    }

    public synchronized boolean isQuorumSatisfied() {
        int healthy = 0;
        for (ServerNode node : nodes.values()) {
            if (node.isListenerRunning() && node.getState() != NodeState.DEAD) {
                healthy++;
            }
        }
        return healthy >= getRequiredQuorum();
    }

    public synchronized Map<String, Object> runScenario(String scenario) {
        String name = scenario == null ? "" : scenario.trim().toLowerCase();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Scenario is required");
        }

        Map<String, Object> result = new ConcurrentHashMap<>();
        recordEvent("SCENARIO_STARTED", "Scenario started: " + name);

        switch (name) {
            case "replication-test":
                if (nodes.size() < 2) {
                    throw new IllegalStateException("Need at least 2 nodes for replication test");
                }
                List<String> active = getActiveNodeIds();
                sendMessage(active.get(0), active.get(1), "Replication consistency check");
                result.put("summary", "Replication test completed");
                break;
            case "leader-failure-and-recovery":
                ServerNode leader = getLeader();
                if (leader != null) {
                    killNode(leader.getNodeId());
                    recoverNode(leader.getNodeId());
                }
                result.put("summary", "Leader failure and recovery executed");
                break;
            case "multiple-failures":
                List<String> activeNodes = getActiveNodeIds();
                if (activeNodes.size() > 2) {
                    killNode(activeNodes.get(0));
                    killNode(activeNodes.get(1));
                    recoverNode(activeNodes.get(0));
                    recoverNode(activeNodes.get(1));
                }
                result.put("summary", "Multiple failure scenario executed");
                break;
            case "message-ordering-test":
                List<String> ids = getActiveNodeIds();
                if (ids.size() < 2) {
                    throw new IllegalStateException("Need at least 2 nodes for ordering test");
                }
                sendMessage(ids.get(0), ids.get(1), "T1: request accepted");
                sendMessage(ids.get(0), ids.get(1), "T2: replication ack");
                sendMessage(ids.get(0), ids.get(1), "T3: ordering validated");
                result.put("summary", "Message ordering test completed");
                break;
            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }

        recordEvent("SCENARIO_COMPLETED", "Scenario completed: " + name);
        result.put("ok", true);
        return result;
    }

    public synchronized String describeNodeStatuses() {
        List<NodeStatus> ordered = getNodeStatuses();

        StringBuilder sb = new StringBuilder();
        sb.append("Node statuses (count=").append(ordered.size()).append(")");
        for (NodeStatus node : ordered) {
            sb.append(System.lineSeparator())
              .append("- ")
              .append(node.nodeId)
              .append(" @ ")
              .append(node.host)
              .append(":")
              .append(node.port)
              .append(" | state=")
              .append(node.state)
              .append(" | listener=")
              .append(node.listenerRunning)
              .append(" | alive=")
              .append(node.alive)
              .append(" | inQ=")
              .append(node.inboundQueue)
              .append(" | outQ=")
              .append(node.outboundQueue)
              .append(" | stored=")
              .append(node.storedMessages);
        }
        return sb.toString();
    }

    public synchronized List<NodeStatus> getNodeStatuses() {
        List<ServerNode> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparing(ServerNode::getNodeId));

        List<NodeStatus> statuses = new ArrayList<>();
        for (ServerNode node : ordered) {
            boolean alive = failureDetector == null || failureDetector.isNodeAlive(node.getNodeId());
            statuses.add(new NodeStatus(
                    node.getNodeId(),
                    node.getHost(),
                    node.getPort(),
                    node.getState().name(),
                    node.isListenerRunning(),
                    alive,
                    node.getInboundQueueSize(),
                    node.getOutboundQueueSize(),
                    node.getStoredMessageCount(),
                    node.getDirectStoredCount(),
                    node.getReplicaStoredCount()));
        }
        return statuses;
    }

    public synchronized List<ClusterEvent> getRecentEvents(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        List<ClusterEvent> out = new ArrayList<>();
        int count = 0;
        for (ClusterEvent event : events) {
            out.add(event);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return out;
    }

    public synchronized long getCurrentTerm() {
        return leaderElection == null ? 0L : leaderElection.getCurrentTerm();
    }

    public void notifyReplication(String failedNodeId) {
        logger.debug("Replication module notified of node failure: {}", failedNodeId);
    }

    public void notifyConsensus(String failedNodeId) {
        logger.debug("Consensus module notified of node failure: {}", failedNodeId);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public Config getConfig() {
        return config;
    }

    private void bootstrapNodesFromConfig() {
        if (!nodes.isEmpty()) {
            return;
        }

        List<NodeEndpoint> endpoints = config.getClusterNodes();
        for (NodeEndpoint endpoint : endpoints) {
            nodes.put(endpoint.getNodeId(), new ServerNode(endpoint.getNodeId(), endpoint.getHost(), endpoint.getPort()));
            knownEndpoints.put(endpoint.getNodeId(), endpoint);
        }
    }

    private void wireNodePeers() {
        for (ServerNode node : nodes.values()) {
            node.clearPeers();
            for (ServerNode peer : nodes.values()) {
                if (!node.getNodeId().equals(peer.getNodeId())) {
                    node.registerPeer(peer);
                }
            }
        }
    }

    private boolean isPortInUse(int port, String ignoreNodeId) {
        for (ServerNode node : nodes.values()) {
            if (!node.getNodeId().equals(ignoreNodeId) && node.getPort() == port) {
                return true;
            }
        }
        return false;
    }

    private Integer parseDefaultNodeSuffix(String nodeId) {
        if (nodeId == null || !nodeId.startsWith("node")) {
            return null;
        }
        try {
            return Integer.valueOf(nodeId.substring(4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateConfigFromNodes() {
        List<ServerNode> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparing(ServerNode::getNodeId));

        List<NodeEndpoint> endpoints = new ArrayList<>();
        for (ServerNode node : ordered) {
            endpoints.add(new NodeEndpoint(node.getNodeId(), node.getHost(), node.getPort()));
        }

        config.setClusterNodes(endpoints);
        config.setNodeCount(endpoints.size());
    }

    private void electLeaderIfPossible() {
        if (leaderElection == null) {
            return;
        }

        ServerNode previous = leaderElection.getCurrentLeader();
        ServerNode elected = leaderElection.startElection(new ArrayList<>(nodes.values()));
        String prevId = previous == null ? "none" : previous.getNodeId();
        String newId = elected == null ? "none" : elected.getNodeId();
        if (!prevId.equals(newId)) {
            recordEvent("LEADER_ELECTED", "Leader changed from " + prevId + " to " + newId + " (term " + getCurrentTerm() + ")");
        }
    }

    private void startLeaderReconciliationTask() {
        if (executor == null) {
            return;
        }

        executor.scheduleAtFixedRate(() -> {
            if (!isRunning) {
                return;
            }

            synchronized (MessagingServer.this) {
                if (leaderElection == null) {
                    return;
                }

                ServerNode current = leaderElection.getCurrentLeader();
                if (current == null || !current.isHealthy()) {
                    electLeaderIfPossible();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void recordEvent(String type, String details) {
        events.addFirst(new ClusterEvent(System.currentTimeMillis(), type, details));
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    private String formatTime(long millis) {
        java.time.LocalTime localTime = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime();
        return localTime.toString();
    }
}
