package com.ds.messaging.replication;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.NodeState;
import com.ds.messaging.server.ServerNode;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class ConsistencyHandlerTest {

    @Test
    public void testWriteQuorumBoundary() {
        ReplicationManager manager = new ReplicationManager(null);
        manager.confirmReplication("m1", "n1");
        manager.confirmReplication("m1", "n2");

        ConsistencyHandler handler = new ConsistencyHandler(manager);
        Assert.assertTrue(handler.writeQuorum("m1", 2));
        Assert.assertFalse(handler.writeQuorum("m1", 3));

        manager.shutdown();
    }

    @Test
    public void testReadQuorumReturnsMostRecent() {
        ConsistencyHandler handler = new ConsistencyHandler();

        ServerNode n1 = node("n1");
        ServerNode n2 = node("n2");
        ServerNode n3 = node("n3");

        Message older = new Message("s", "v1");
        older.setLogicalClock(2);
        Message newer = new Message("s", "v2");
        newer.setLogicalClock(9);

        handler.recordReplicaMessage("msg", "n1", older);
        handler.recordReplicaMessage("msg", "n2", newer);
        handler.recordReplicaMessage("msg", "n3", older);

        Message latest = handler.readQuorum("msg", Arrays.asList(n1, n2, n3));
        Assert.assertNotNull(latest);
        Assert.assertEquals(9, latest.getLogicalClock());
    }

    @Test
    public void testEnsureConsistencyAndConvergence() {
        ConsistencyHandler handler = new ConsistencyHandler();

        Message older = new Message("s", "old");
        older.setLogicalClock(1);
        Message newer = new Message("s", "new");
        newer.setLogicalClock(5);

        handler.recordReplicaMessage("msg2", "n1", older);
        handler.recordReplicaMessage("msg2", "n2", newer);

        Assert.assertFalse(handler.checkConvergence());
        handler.ensureConsistency("msg2");
        Assert.assertTrue(handler.checkConvergence());
    }

    private ServerNode node(String id) {
        ServerNode node = new ServerNode(id, "localhost", 9000);
        node.setState(NodeState.READY);
        return node;
    }
}
