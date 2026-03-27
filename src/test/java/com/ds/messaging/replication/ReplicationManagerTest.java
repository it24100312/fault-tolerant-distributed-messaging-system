package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.FailureDetector;
import com.ds.messaging.server.LeaderElection;
import com.ds.messaging.server.NodeState;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.time.MessageOrderer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Test;

public class ReplicationManagerTest {

    @Test
    public void testQuorumCalculation() {
        Assert.assertEquals(1, ReplicationManager.calculateQuorum(1));
        Assert.assertEquals(2, ReplicationManager.calculateQuorum(2));
        Assert.assertEquals(2, ReplicationManager.calculateQuorum(3));
        Assert.assertEquals(3, ReplicationManager.calculateQuorum(5));
    }

    @Test
    public void testSelectHealthyReplicasExcludesFailedNodes() {
        LeaderElection election = new LeaderElection();
        FailureDetector detector = new FailureDetector(election);

        ServerNode n1 = readyNode("n1");
        ServerNode n2 = readyNode("n2");
        ServerNode n3 = readyNode("n3");

        detector.startHeartbeat(n1);
        detector.startHeartbeat(n2);
        detector.startHeartbeat(n3);
        detector.onHeartbeatMissed("n2");
        detector.onHeartbeatMissed("n2");
        detector.onHeartbeatMissed("n2");

        ReplicationManager manager = new ReplicationManager(detector);
        List<ServerNode> healthy = manager.selectHealthyReplicas(List.of(n1, n2, n3));

        Assert.assertEquals(2, healthy.size());
        Assert.assertFalse(healthy.stream().anyMatch(n -> n.getNodeId().equals("n2")));

        detector.shutdown();
        manager.shutdown();
    }

    @Test
    public void testReplicateToQuorumSuccess() {
        ReplicationManager manager = new ReplicationManager(null);
        List<ServerNode> nodes = List.of(readyNode("n1"), readyNode("n2"), readyNode("n3"));
        Message msg = new Message("client", "payload");

        manager.replicateMessage(msg, nodes);

        Assert.assertTrue(manager.isMessageReplicated(msg.getMessageId()));
        Assert.assertTrue(manager.getReplicationCount(msg.getMessageId()) >= 2);
        manager.shutdown();
    }

    @Test
    public void testTimeoutPathBelowQuorum() {
        MessageReplicator noAckReplicator = new MessageReplicator() {
            @Override
            public void replicate(Message msg, ServerNode target) {
                onReplicationFail(msg.getMessageId(), target.getNodeId());
            }
        };

        ReplicationManager manager = new ReplicationManager(
                null,
                noAckReplicator,
                new MessageDeduplicator(),
                null,
                null);
        manager.setReplicationTimeoutMs(300);

        List<ServerNode> nodes = List.of(readyNode("n1"), readyNode("n2"), readyNode("n3"));
        Message msg = new Message("client", "will-timeout");

        manager.replicateMessage(msg, nodes);

        Assert.assertFalse(manager.isMessageReplicated(msg.getMessageId()));
        manager.shutdown();
    }

    @Test
    public void testConcurrentReplicationIndependentTracking() throws Exception {
        ReplicationManager manager = new ReplicationManager(null);
        List<ServerNode> nodes = List.of(readyNode("n1"), readyNode("n2"), readyNode("n3"));

        int total = 20;
        CountDownLatch latch = new CountDownLatch(total);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Message> messages = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            Message msg = new Message("client", "m-" + i);
            messages.add(msg);
            pool.submit(() -> {
                manager.replicateMessage(msg, nodes);
                latch.countDown();
            });
        }

        Assert.assertTrue(latch.await(4, TimeUnit.SECONDS));
        for (Message msg : messages) {
            Assert.assertTrue(manager.isMessageReplicated(msg.getMessageId()));
        }

        pool.shutdownNow();
        manager.shutdown();
    }

    @Test
    public void testReplicationInvokesOrderingBeforeReplicaWrite() {
        AtomicBoolean observedUntimestampedMessage = new AtomicBoolean(false);

        MessageReplicator checkingReplicator = new MessageReplicator() {
            @Override
            public void replicate(Message msg, ServerNode target) {
                if (msg.getLogicalClock() <= 0L) {
                    observedUntimestampedMessage.set(true);
                }
                super.replicate(msg, target);
            }
        };

        MessageOrderer orderer = new MessageOrderer();
        ReplicationManager manager = new ReplicationManager(
                null,
                checkingReplicator,
                new MessageDeduplicator(),
                orderer,
                null);

        List<ServerNode> nodes = List.of(readyNode("n1"), readyNode("n2"), readyNode("n3"));
        Message first = new Message("client", "first");
        Message second = new Message("client", "second");

        manager.replicateMessage(first, nodes);
        manager.replicateMessage(second, nodes);

        Assert.assertFalse("Messages should be timestamped before replication", observedUntimestampedMessage.get());
        Assert.assertTrue("First message should get logical timestamp", first.getLogicalClock() > 0L);
        Assert.assertTrue("Second message should get logical timestamp", second.getLogicalClock() > first.getLogicalClock());
        Assert.assertTrue("First message should reach quorum", manager.isMessageReplicated(first.getMessageId()));
        Assert.assertTrue("Second message should reach quorum", manager.isMessageReplicated(second.getMessageId()));

        manager.shutdown();
    }

    private static ServerNode readyNode(String id) {
        ServerNode node = new ServerNode(id, "localhost", 9000);
        node.setState(NodeState.READY);
        return node;
    }
}
