package com.ds.messaging.consensus;

/**
 * Marker abstraction for Raft RPC requests.
 */
public interface RPC {
    int getTerm();
}
