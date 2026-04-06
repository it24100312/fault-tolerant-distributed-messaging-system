package com.ds.messaging.consensus;

/**
 * Node role in Raft.
 */
public enum RaftState {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
