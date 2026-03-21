package com.ds.messaging.server;

/**
 * Node lifecycle states for cluster membership and health checks.
 */
public enum NodeState {
    STARTING,
    READY,
    SYNCING,
    UNHEALTHY,
    SHUTTING_DOWN,
    DEAD
}
