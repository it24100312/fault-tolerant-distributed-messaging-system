package com.ds.messaging.time;

import com.ds.messaging.client.Message;
import com.ds.messaging.replication.OrderingPort;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns and tracks logical timestamps for message ordering.
 *
 * Commit 1 scope: scaffolding + method contracts only.
 */
public class MessageOrderer implements OrderingPort {
    private static final Logger logger = Logger.getInstance();

    private final AtomicLong logicalClock = new AtomicLong(0L);

    /**
     * Assign Lamport timestamp to a message before replication.
     */
    public void assignTimestamp(Message msg, ServerNode originator) {
        if (msg == null) {
            return;
        }

        long assigned = logicalClock.incrementAndGet();
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
     *
     * Commit 1 scope: returns input as-is; final ordering logic is added later.
     */
    public List<Message> orderMessages(List<Message> msgList) {
        if (msgList == null || msgList.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(msgList);
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
