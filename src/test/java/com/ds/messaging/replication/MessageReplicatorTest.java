package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.NodeState;
import com.ds.messaging.server.ServerNode;
import org.junit.Assert;
import org.junit.Test;

public class MessageReplicatorTest {

    @Test
    public void testAckReceivedForHealthyNode() {
        MessageReplicator replicator = new MessageReplicator();
        ServerNode node = new ServerNode("n1", "localhost", 9001);
        node.setState(NodeState.READY);

        Message msg = new Message("sender", "hello");
        replicator.replicate(msg, node);

        boolean ack = replicator.waitForAck(MessageReplicator.ackKey(msg.getMessageId(), "n1"), 300);
        Assert.assertTrue(ack);
    }

    @Test
    public void testAckTimeoutForUnknownMessage() {
        MessageReplicator replicator = new MessageReplicator();
        boolean ack = replicator.waitForAck("unknown-message", 50);
        Assert.assertFalse(ack);
    }

    @Test
    public void testReplicationFailTracking() {
        MessageReplicator replicator = new MessageReplicator();
        ServerNode node = new ServerNode("n2", "localhost", 9002);
        node.setState(NodeState.STARTING);

        Message msg = new Message("sender", "payload");
        replicator.replicate(msg, node);

        Assert.assertTrue(replicator.getFailedNodes(msg.getMessageId()).contains("n2"));
    }

    @Test
    public void testRetryEventuallySucceeds() {
        MessageReplicator replicator = new MessageReplicator();
        ServerNode node = new ServerNode("n3", "localhost", 9003);
        node.setState(NodeState.STARTING);

        Message msg = new Message("sender", "retry");
        replicator.replicate(msg, node);
        node.setState(NodeState.READY);

        replicator.retryReplication(msg.getMessageId());
        boolean ack = replicator.waitForAck(MessageReplicator.ackKey(msg.getMessageId(), "n3"), 1000);

        Assert.assertTrue(ack);
        Assert.assertTrue(replicator.getAttemptCount(msg.getMessageId()) >= 2);
    }
}
