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
    private final ReentrantLock electionLock = new ReentrantLock();
    private final Condition electionComplete = electionLock.newCondition();
    
    // Election timeout and retry parameters
    private static final long ELECTION_TIMEOUT_MS = 5000;
    private static final int MAX_ELECTION_ROUNDS = 3;
    
    /**
     * Start leader election among given candidates
     * 
     * Uses Bully Algorithm: node with highest ID/priority becomes leader
     * 
     * @param candidates List of candidate nodes
     * @return Elected leader, or null if election fails
     */
    public ServerNode startElection(List<ServerNode> candidates) {
        electionLock.lock();
        try {
            logger.info("Starting leader election among {} candidates", candidates.size());
            
            // Increment term for new election
            currentTerm++;
            
            if (candidates.isEmpty()) {
                logger.warn("No candidates available for election!");
                return null;
            }
            
            // Sort candidates by nodeId (lexicographic order - highest priority)
            candidates.sort((a, b) -> b.getNodeId().compareTo(a.getNodeId()));
            
            // First candidate (highest ID) wins election (Bully algorithm)
            ServerNode newLeader = candidates.get(0);
            
            // Verify candidate is healthy
            if (newLeader.isHealthy()) {
                currentLeader = newLeader;
                logger.info("New leader elected: {} (term: {})", newLeader.getNodeId(), currentTerm);
                electionComplete.signalAll();
                return newLeader;
            } else {
                // Try next candidate if current one is not healthy
                for (ServerNode candidate : candidates) {
                    if (candidate.isHealthy()) {
                        currentLeader = candidate;
                        logger.info("New leader elected: {} (term: {})", candidate.getNodeId(), currentTerm);
                        electionComplete.signalAll();
                        return candidate;
                    }
                }
                
                logger.error("No healthy candidates available!");
     
    
    /**
     * Get current election term
     */
    public long getCurrentTerm() {
        return currentTerm;
    }           return null;
            }
        } finally {
            electionLock.unlock();
        }
    }
    
    /**
     * Get current cluster leader
     */
    public ServerNode getCurrentLeader() {
        if (currentLeader == null) {
            return false;
        }
    
    /**
     * Check if current leader is alive and healthy
     */
    pu Called by FailureDetector when current leader detected as dead
     */
    public void triggerReelection() {
        electionLock.lock();
        try {
            currentLeader = null;  // Clear old leader
            currentTerm++;         // Start new term
            
            logger.warn("Re-election triggered! New term: {}", currentTerm);
            
            // Note: Actual election will be started by MessagingServer
            // which will provide list of healthy candidates
        } finally {
            electionLock.unlock();
        }
     * Manually trigger re-election (e.g., when leader dies)
     */
    public void triggerReelection() {testing or consensus integration)
     */
    public void setLeader(ServerNode leader) {
        electionLock.lock();
        try {
            currentLeader = leader;
            if (leader != null) {
                logger.info("Leader set to: {}", leader.getNodeId());
            }
        } finally {
            electionLock.unlock();
        }
    }
    
    /**
     * Wait for leader to be elected (blocks until leader exists)
     */
    public void waitForLeader(long timeoutMs) throws InterruptedException {
        electionLock.lock();
        try {
            electionComplete.await(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            electionLock.unlock();
        }
    }
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
