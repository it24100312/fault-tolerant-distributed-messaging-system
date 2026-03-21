package com.ds.messaging.server;

import com.ds.messaging.utils.Logger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distributed leader election mechanism.
 * 
 * From LAB 6: Implement a protocol where nodes elect a single leader/coordinator.
 * 
 * Responsibilities:
 * - Elect a unique leader from candidate nodes
 * - Handle leader failure and re-election
 * - Ensure safety (at most one leader) and liveness (always elect a leader)
 * - Track current leader and term/generation
 * 
 * TODO: Implement leader election algorithm (e.g., Bully Algorithm or Ring Algorithm)
 */
public class LeaderElection {
    private static final Logger logger = Logger.getInstance();
    
    private volatile ServerNode currentLeader;
    private volatile long currentTerm = 0;
    private ReentrantLock electionLock = new ReentrantLock();
    private Condition electionComplete = electionLock.newCondition();
    
    // Election timeout and retry parameters
    private static final long ELECTION_TIMEOUT_MS = 5000;
    private static final int MAX_ELECTION_ROUNDS = 3;
    
    /**
     * Start leader election among given candidates
     * 
     * @param candidates List of candidate nodes
     * @return Elected leader, or null if election fails
     */
    public ServerNode startElection(List<ServerNode> candidates) {
        // TODO: Implement election algorithm
        // 
        // Example approach (Bully Algorithm):
        // 1. For each node, try to claim leadership based on node ID/priority
        // 2. Candidates with higher priority take precedence
        // 3. Send election messages to all lower-priority nodes
        // 4. If no one objects, current node becomes leader
        // 5. Announce new leader to all
        //
        // Safety checks:
        // - Only elect from healthy (ready) nodes
        // - Increment currentTerm on each re-election
        // - Ensure only one leader exists
        
        electionLock.lock();
        try {
            logger.info("Starting leader election among {} candidates", candidates.size());
            currentTerm++;
            
            ServerNode newLeader = null;
            // TODO: Implement the actual election logic here
            
            if (newLeader != null) {
                currentLeader = newLeader;
                logger.info("New leader elected: {} (term: {})", newLeader.getNodeId(), currentTerm);
                electionComplete.signalAll();
            }
            return newLeader;
        } finally {
            electionLock.unlock();
        }
    }
    
    /**
     * Get current cluster leader
     */
    public ServerNode getCurrentLeader() {
        return currentLeader;
    }
    
    /**
     * Check if current leader is alive and healthy
     */
    public boolean isLeaderAlive() {
        // TODO: Implement leader health check
        if (currentLeader == null) return false;
        return currentLeader.isHealthy();
    }
    
    /**
     * Manually trigger re-election (e.g., when leader dies)
     */
    public void triggerReelection() {
        // TODO: Implement re-election trigger
        // 1. Clear current leader
        // 2. Start new election round
        currentLeader = null;
        logger.warn("Re-election triggered!");
    }
    
    /**
     * Set the leader explicitly (for consensus integration)
     */
    public void setLeader(ServerNode leader) {
        // TODO: Implement leader assignment
        this.currentLeader = leader;
        logger.info("Leader set to: {}", leader.getNodeId());
    }
    
    /**
     * Get current election term/generation
     */
    public long getCurrentTerm() {
        return currentTerm;
    }
    
    /**
     * Wait for election to complete (with timeout)
     */
    public void waitForLeader(long timeoutMs) throws InterruptedException {
        // TODO: Implement wait logic
        electionLock.lock();
        try {
            while (currentLeader == null) {
                electionComplete.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            electionLock.unlock();
        }
    }
    
}
