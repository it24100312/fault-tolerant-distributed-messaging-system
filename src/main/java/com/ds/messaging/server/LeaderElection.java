package com.ds.messaging.server;

import com.ds.messaging.utils.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distributed leader election mechanism.
 *
 * Commit 2 scope:
 * - Deterministic leader selection from healthy candidates
 * - Re-election trigger on leader failure
 * - Term tracking and leader liveness checks
 */
public class LeaderElection {
    private static final Logger logger = Logger.getInstance();

    private volatile ServerNode currentLeader;
    private volatile long currentTerm = 0L;

    private final ReentrantLock electionLock = new ReentrantLock();
    private final Condition electionComplete = electionLock.newCondition();

    /**
     * Start an election among candidates and choose the highest nodeId among healthy nodes.
     */
    public ServerNode startElection(List<ServerNode> candidates) {
        electionLock.lock();
        try {
            currentTerm++;

            if (candidates == null || candidates.isEmpty()) {
                currentLeader = null;
                logger.warn("No candidates provided for election in term {}", currentTerm);
                return null;
            }

            List<ServerNode> healthyCandidates = new ArrayList<>();
            for (ServerNode candidate : candidates) {
                if (candidate != null && candidate.isHealthy()) {
                    healthyCandidates.add(candidate);
                }
            }

            if (healthyCandidates.isEmpty()) {
                currentLeader = null;
                logger.warn("No healthy candidates available for election in term {}", currentTerm);
                return null;
            }

            healthyCandidates.sort(Comparator.comparing(ServerNode::getNodeId).reversed());
            currentLeader = healthyCandidates.get(0);
            electionComplete.signalAll();

            logger.info("Leader elected: {} (term: {})", currentLeader.getNodeId(), currentTerm);
            return currentLeader;
        } finally {
            electionLock.unlock();
        }
    }

    /**
     * Trigger re-election by clearing leader and incrementing term.
     */
    public void triggerReelection() {
        electionLock.lock();
        try {
            currentLeader = null;
            currentTerm++;
            electionComplete.signalAll();
            logger.warn("Re-election triggered (term: {})", currentTerm);
        } finally {
            electionLock.unlock();
        }
    }

    /**
     * Called by failure detector when a leader is suspected failed.
     */
    public void onLeaderFailure(String nodeId) {
        if (nodeId == null) {
            return;
        }

        electionLock.lock();
        try {
            if (currentLeader != null && nodeId.equals(currentLeader.getNodeId())) {
                currentLeader = null;
                currentTerm++;
                electionComplete.signalAll();
                logger.warn("Leader failure detected for node {}. New term {}", nodeId, currentTerm);
            }
        } finally {
            electionLock.unlock();
        }
    }

    public ServerNode getCurrentLeader() {
        return currentLeader;
    }

    public boolean isLeaderAlive() {
        ServerNode leader = currentLeader;
        return leader != null && leader.isHealthy();
    }

    public void setLeader(ServerNode leader) {
        electionLock.lock();
        try {
            currentLeader = leader;
            if (leader != null) {
                logger.info("Leader set to: {}", leader.getNodeId());
                electionComplete.signalAll();
            }
        } finally {
            electionLock.unlock();
        }
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public void waitForLeader(long timeoutMs) throws InterruptedException {
        electionLock.lock();
        try {
            if (currentLeader == null) {
                electionComplete.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            electionLock.unlock();
        }
    }
}
