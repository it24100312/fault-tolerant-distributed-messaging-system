package com.ds.messaging.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.ds.messaging.client.Message;
import com.ds.messaging.server.ServerNode;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class MessageOrdererTest {

    @Test
    public void testAssignTimestampUsesLamportMaxRule() {
        MessageOrderer orderer = new MessageOrderer();

        orderer.incrementClock();
        orderer.incrementClock();

        Message incoming = new Message("node-a", "hello");
        incoming.setLogicalClock(10L);

        orderer.assignTimestamp(incoming, new ServerNode("node-a", "localhost", 5001));

        assertEquals("Assigned timestamp should follow max(local, incoming) + 1", 11L, incoming.getLogicalClock());
        assertEquals("Local logical clock should track assigned timestamp", 11L, orderer.getLogicalClock());
    }

    @Test
    public void testAssignTimestampMonotonicAcrossMessages() {
        MessageOrderer orderer = new MessageOrderer();

        Message first = new Message("node-a", "m1");
        Message second = new Message("node-a", "m2");

        orderer.assignTimestamp(first, null);
        orderer.assignTimestamp(second, null);

        assertTrue("Second message must have greater logical clock", second.getLogicalClock() > first.getLogicalClock());
    }

    @Test
    public void testOrderMessagesByTimestampThenSenderId() {
        MessageOrderer orderer = new MessageOrderer();

        Message msgFromB = new Message("B", "from-b");
        msgFromB.setLogicalClock(5L);

        Message msgFromA = new Message("A", "from-a");
        msgFromA.setLogicalClock(5L);

        Message later = new Message("C", "later");
        later.setLogicalClock(7L);

        List<Message> input = Arrays.asList(later, msgFromB, msgFromA);
        List<Message> ordered = orderer.orderMessages(input);

        assertNotSame("Ordering should return a new list", input, ordered);
        assertEquals("First message should be sender A at same logical timestamp", "A", ordered.get(0).getSenderId());
        assertEquals("Second message should be sender B at same logical timestamp", "B", ordered.get(1).getSenderId());
        assertEquals("Last should be higher logical timestamp", "C", ordered.get(2).getSenderId());
    }

    @Test
    public void testOrderMessagesHandlesNullAndEmpty() {
        MessageOrderer orderer = new MessageOrderer();

        assertTrue(orderer.orderMessages(null).isEmpty());
        assertTrue(orderer.orderMessages(Arrays.asList()).isEmpty());
    }
}
