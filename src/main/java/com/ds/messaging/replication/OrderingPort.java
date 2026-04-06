package com.ds.messaging.replication;

import com.ds.messaging.client.Message;

/**
 * Optional adapter for message ordering integration.
 */
public interface OrderingPort {
    void assignTimestamp(Message message);
}
