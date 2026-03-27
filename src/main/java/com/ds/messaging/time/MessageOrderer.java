package com.ds.messaging.time;

import com.ds.messaging.client.Message;
import com.ds.messaging.replication.OrderingPort;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns and tracks logical timestamps for message ordering.
 */
public class MessageOrderer implements OrderingPort {
    private static final Logger logger = Logger.getInstance();

    private static final Comparator<Message> TOTAL_ORDER =
        Comparator.comparingLong(Message::getLogicalClock)
            .thenComparing(
                Message::getSenderId,
                Comparator.nullsLast(String::compareTo))
            .thenComparingLong(Message::getPhysicalTimestamp)
            .thenComparing(
                Message::getMessageId,
                Comparator.nullsLast(String::compareTo));

    private final AtomicLong logicalClock = new AtomicLong(0L);

    /**
     * Assign Lamport timestamp to a message before replication.
     */
    public void assignTimestamp(Message msg, ServerNode originator) {
        if (msg == null) {
            return;
        }

        long incomingTimestamp = Math.max(0L, msg.getLogicalClock());
        long assigned = logicalClock.updateAndGet(current -> Math.max(current, incomingTimestamp) + 1L);
        msg.setLogicalClock(assigned);

        if (originator != null) {
            logger.debug("Assigned logical clock {} to message {} from {}",
                    assigned, msg.getMessageId(), originator.getNodeId());
        } else {
            logger.debug("Assigned logical clock {} to message {}",
                    assigned, msg.getMessageId());
        }
    }

    /**
     * OrderingPort adapter used by replication flow.
     */
    @Override
    public void assignTimestamp(Message msg) {
        assignTimestamp(msg, null);
    }

    /**
     * Return messages in deterministic order.
     */
    public List<Message> orderMessages(List<Message> msgList) {
        if (msgList == null || msgList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> ordered = new ArrayList<>(msgList);
        ordered.sort(TOTAL_ORDER);
        return ordered;
    }

    /**
     * Get current logical clock value.
     */
    public long getLogicalClock() {
        return logicalClock.get();
    }

    /**
     * Increment logical clock for local events.
     */
    public void incrementClock() {
        logicalClock.incrementAndGet();
    }
}
