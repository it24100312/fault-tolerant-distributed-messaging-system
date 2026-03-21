package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.ServerNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quorum consistency coordinator for write/read verification.
 */
public class ConsistencyHandler {
    private final ReplicationManager replicationManager;

    // messageId -> (nodeId -> messageVersion)
    private final Map<String, Map<String, Message>> replicaVersions = new ConcurrentHashMap<>();

    public ConsistencyHandler() {
        this(null);
    }

    public ConsistencyHandler(ReplicationManager replicationManager) {
        this.replicationManager = replicationManager;
    }

    public boolean writeQuorum(String messageId, int quorumSize) {
        if (replicationManager == null || messageId == null || quorumSize <= 0) {
            return false;
        }
        return replicationManager.getReplicationCount(messageId) >= quorumSize;
    }

    public Message readQuorum(String messageId, List<ServerNode> nodes) {
        if (messageId == null || nodes == null || nodes.isEmpty()) {
            return null;
        }

        int quorum = ReplicationManager.calculateQuorum(nodes.size());
        List<Message> versions = new ArrayList<>();

        Map<String, Message> byNode = replicaVersions.getOrDefault(messageId, new ConcurrentHashMap<>());
        for (ServerNode node : nodes) {
            Message candidate = byNode.get(node.getNodeId());
            if (candidate != null) {
                versions.add(candidate);
            }
        }

        if (versions.size() < quorum) {
            return null;
        }

        Message latest = versions.get(0);
        for (int i = 1; i < versions.size(); i++) {
            latest = newerOf(latest, versions.get(i));
        }
        return latest;
    }

    public void ensureConsistency(String messageId) {
        Map<String, Message> versions = replicaVersions.get(messageId);
        if (versions == null || versions.isEmpty()) {
            return;
        }

        Message latest = null;
        for (Message message : versions.values()) {
            latest = latest == null ? message : newerOf(latest, message);
        }

        if (latest == null) {
            return;
        }

        for (Map.Entry<String, Message> entry : versions.entrySet()) {
            if (!sameVersion(entry.getValue(), latest)) {
                versions.put(entry.getKey(), latest);
            }
        }
    }

    public boolean checkConvergence() {
        for (Map<String, Message> versions : replicaVersions.values()) {
            if (versions.isEmpty()) {
                continue;
            }
            Message baseline = null;
            for (Message message : versions.values()) {
                if (baseline == null) {
                    baseline = message;
                } else if (!sameVersion(baseline, message)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void recordReplicaMessage(String messageId, String nodeId, Message message) {
        if (messageId == null || nodeId == null || message == null) {
            return;
        }
        replicaVersions.computeIfAbsent(messageId, k -> new ConcurrentHashMap<>()).put(nodeId, message);
    }

    private Message newerOf(Message a, Message b) {
        if (a.getLogicalClock() != b.getLogicalClock()) {
            return a.getLogicalClock() > b.getLogicalClock() ? a : b;
        }
        if (a.getPhysicalTimestamp() != b.getPhysicalTimestamp()) {
            return a.getPhysicalTimestamp() > b.getPhysicalTimestamp() ? a : b;
        }
        return a;
    }

    private boolean sameVersion(Message a, Message b) {
        return a.getMessageId().equals(b.getMessageId())
                && a.getLogicalClock() == b.getLogicalClock()
                && a.getPhysicalTimestamp() == b.getPhysicalTimestamp()
                && a.getContent().equals(b.getContent());
    }
}
