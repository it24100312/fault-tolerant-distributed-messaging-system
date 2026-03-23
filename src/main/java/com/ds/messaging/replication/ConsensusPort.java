package com.ds.messaging.replication;

/**
 * Optional adapter for consensus integration after quorum replication.
 */
public interface ConsensusPort {
    void onMessageReplicated(String messageId);
}
