package com.ds.messaging.server;

/**
 * Represents the state of a server node in the cluster.
 */
public enum NodeState {
    STARTING,       // Node is initializing
    READY,          // Node is operational and ready to receive messages
    SYNCING,        // Node is syncing state with leader
    SHUTTING_DOWN,  // Node is gracefully shutting down
    DEAD;           // Node is no longer operational
}
