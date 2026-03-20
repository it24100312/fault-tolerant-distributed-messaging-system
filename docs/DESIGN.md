# DISTRIBUTED MESSAGING SYSTEM - ARCHITECTURE & DESIGN

**Project:** Distributed Messaging System  
**Based on:** SLIIT DS Module Labs (Lab 3-6)  
**Semester:** Year 2, Semester 2  
**Date:** March 20, 2026  
**Status:** ACTIVE - READY FOR IMPLEMENTATION

---

## 1. PROJECT OVERVIEW

### 1.1 Objective
Build a **distributed messaging system** that enables:
- Multiple nodes to exchange messages reliably
- Strong consistency guarantees via Raft consensus
- Automatic failure detection and recovery
- Clock synchronization across nodes
- At-least-once message delivery with deduplication

### 1.2 Scope
**Included:**
- ✅ Multi-node messaging cluster (3-5 nodes)
- ✅ Leader election (from LAB 6)
- ✅ Message replication (from LAB 5)
- ✅ Clock synchronization (from LAB 4)
- ✅ Raft consensus algorithm
- ✅ Failure detection (heartbeat)
- ✅ Client interface
- ✅ Comprehensive testing

**Excluded:**
- ❌ Persistence to disk (in-memory only)
- ❌ Web UI (CLI only)
- ❌ Byzantine fault tolerance
- ❌ Encryption/security (focus on correctness)

### 1.3 Key Requirements
**Functional:**
1. Nodes can send messages to cluster
2. Messages replicated to quorum of nodes
3. Leader elected automatically
4. Failed nodes detected within 10 seconds
5. Strong consistency: all nodes see same messages in same order
6. No message duplicates (even with retries)

**Non-Functional:**
1. Throughput: > 1000 messages/second
2. Latency: < 100ms from send to committed
3. Availability: survive 1 node failure (3+ nodes)
4. Clock skew: < 10ms after synchronization
5. Code coverage: > 85% unit test coverage
6. Zero resource leaks (careful thread management)

---

## 2. SYSTEM ARCHITECTURE

### 2.1 High-Level System Diagram
```
┌─────────────────────────────────────────────────────────────────┐
│                      CLIENT APPLICATIONS                        │
│                         (MessagingClient)                       │
└────────────────────────┬────────────────────────────────────────┘
                         │ (Send/Receive Messages)
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼────────┐  ┌────▼────────┐  ┌──▼──────────────┐
│    REPLICATION │  │   TIME SYNC  │  │  CONSENSUS     │
│   (Lab 5)      │  │   (Lab 4)    │  │  (Raft)        │
│                │  │              │  │                │
│ •Replicator    │  │ •ClockSync   │  │ •RaftConsensus │
│ •Quorum        │  │ •MessageOrd  │  │ •LogEntry      │
│ •Dedup         │  │ •NTP         │  │ •CommitIndex   │
└────────────────┘  └─────┬────────┘  └─────┬──────────┘
                          │                  │
        ┌─────────────────┴──────────────────┘
        │
┌───────▼──────────────────────────────────────────────────┐
│          SERVER INFRASTRUCTURE                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │ MessagingServer: Cluster lifecycle management    │   │
│  │ ServerNode: Individual node (ID, state, queues)  │   │
│  │ LeaderElection (Lab 6): Elect coordinator        │   │
│  │ FailureDetector: Heartbeat monitoring (0-3 sec) │   │
│  └──────────────────────────────────────────────────┘   │
├───────────────────────────────────────────────────────────┤
│          UTILITIES & CONFIGURATION (Member 4)            │
│  Config: System parameters (port, timeouts, etc)         │
│  Logger: SLF4J + Logback structured logging              │
└───────────────────────────────────────────────────────────┘
```

### 2.2 Module Responsibilities

| Module | Class | Responsibility | From Lab |
|--------|-------|-----------------|----------|
| **SERVER** | MessagingServer | Cluster lifecycle, node management | - |
| | ServerNode | Single node state, messaging | - |
| | LeaderElection | Elect unique leader, re-election | LAB 6 |
| | FailureDetector | Heartbeat, detect failures | - |
| **REPLICATION** | ReplicationManager | Replicate to quorum | LAB 5 |
| | MessageReplicator | Individual msg replication | LAB 5 |
| | ConsistencyHandler | Read/write quorum consensus | LAB 5 |
| | MessageDeduplicator | Prevent duplicate processing | - |
| **TIME SYNC** | ClockSynchronizer | Berkeley algorithm sync | LAB 4 |
| | NTPClient | Access NTP server | LAB 4 |
| | MessageOrderer | Logical/vector clock ordering | LAB 4 |
| **CONSENSUS** | RaftConsensus | Raft state machine | - |
| | LogEntry | Single log entry data | - |
| | ConsensusManager | Consensus orchestration | - |
| **CLIENT** | MessagingClient | Client applications | - |
| | Message | Message data + serialization | - |
| **UTILS** | Config | Configuration management | LAB 3 |
| | Logger | SLF4J wrapper | - |

---

## 3. DETAILED COMPONENT DESIGN

### 3.1 SERVER INFRASTRUCTURE (Member 1)

#### MessagingServer
**Purpose:** Manage cluster and coordinate between nodes

```
Data:
  - Map<String, ServerNode> nodes        // All nodes in cluster
  - LeaderElection leaderElection        // Coordinator election
  - FailureDetector failureDetector      // Health monitoring
  - boolean isRunning                    // Lifecycle state

Methods:
  + start()                              // Initialize cluster
  + stop()                               // Graceful shutdown
  + addNode(id, host, port)              // Add node to cluster
  + getNode(id) → ServerNode             // Get specific node
  + getAllNodes() → List<ServerNode>     // Get all nodes
  + getLeader() → ServerNode             // Get current leader
```

#### ServerNode  
**Purpose:** Represent individual cluster node

```
Data:
  - String nodeId                        // Unique identifier
  - String host, int port                // Network address
  - NodeState state                      // STARTING/READY/SYNCING/DEAD
  - Queue<Message> inbound, outbound     // Message buffers
  - long lastHeartbeat                   // For failure detection

Methods:
  + initialize()                         // Setup resources
  + connect()                            // Connect to peers
  + sendToNode(targetId, msg)            // Send message
  + receiveMessage() → Message           // Get from inbound
  + handleIncomingMessage(msg)           // Process incoming
  + isHealthy() → boolean                // Health check
  + shutdown()                           // Cleanup
```

#### LeaderElection (LAB 6)
**Purpose:** Ensure exactly one leader

```
Data:
  - volatile ServerNode currentLeader    // Current leader
  - volatile long currentTerm            // Election generation
  - ReentrantLock electionLock           // Thread safety

Methods:
  + startElection(candidates) → ServerNode
  + getCurrentLeader() → ServerNode
  + isLeaderAlive() → boolean
  + triggerReelection()
  + setLeader(node)
  + waitForLeader(timeoutMs)
```

**Algorithm:** Bully or Ring election  
**Safety:** At most 1 leader per term

#### FailureDetector
**Purpose:** Detect and report node failures

```
Data:
  - Map<String, Long> lastHeartbeatTime  // When last seen
  - Map<String, Integer> failureCount    // Failures per node
  - Set<String> failedNodes              // Dead nodes
  - ScheduledExecutorService executor    // Periodic tasks

Constants:
  - HEARTBEAT_INTERVAL = 2000 ms
  - HEARTBEAT_TIMEOUT = 5000 ms
  - MAX_FAILURES_BEFORE_DEAD = 3

Methods:
  + startHeartbeat(node)
  + stopHeartbeat(nodeId)
  + onHeartbeatReceived(nodeId)
  + isNodeAlive(nodeId) → boolean
  + getFailedNodes() → List<String>
  + onHeartbeatMissed(nodeId)
```

**Behavior:** 
- 3 missed heartbeats (6 sec) → mark dead
- Notify leader election to re-elect

---

### 3.2 REPLICATION MODULE (Member 2 - Part A: LAB 5)

#### ReplicationManager
**Purpose:** Coordinate replication strategy

```
Data:
  - Map<String, ReplicationStatus> tracker
  - int quorumSize               // N/2 + 1
  - FailureDetector failureDetector

Methods:
  + replicateMessage(msg, replicas)
  + confirmReplication(msgId, nodeId)
  + isMessageReplicated(msgId) → boolean
  + getReplicationCount(msgId) → int
  + selectHealthyReplicas() → List<ServerNode>
```

#### ConsistencyHandler
**Purpose:** Implement quorum read/write consistency

```
Methods:
  + writeQuorum(msgId, quorumSize) → boolean
  + readQuorum(msgId, nodes) → Message   // Most recent
  + ensureConsistency(msgId)
  + checkConvergence() → boolean
```

#### MessageReplicator
**Purpose:** Handle per-message replication

```
Data:
  - Map<String, ReplicationState> msgStates

Methods:
  + replicate(msg, targetNode)
  + waitForAck(msgId, timeoutMs) → boolean
  + retryReplication(msgId)
  + onReplicationFail(msgId, nodeId)
```

#### MessageDeduplicator
**Purpose:** Prevent processing same message twice

```
Data:
  - Set<String> seenMessageIds (fixed size with LRU eviction)
  - long retentionMs = 3600000 (1 hour)

Methods:
  + isDuplicate(msgId) → boolean
  + addMessageId(msgId)
  + removeOldMessages(retentionMs)
  + clearCache()
```

---

### 3.3 TIME SYNCHRONIZATION MODULE (Member 2 - Part B: LAB 4)

#### ClockSynchronizer
**Purpose:** Keep cluster clocks synchronized

```
Algorithm: Berkeley Algorithm
  1. Leader queries each node for time
  2. Calculate average
  3. Send correction to each node
  4. Run periodically (e.g., every 60 seconds)

Data:
  - long lastSyncTime
  - Map<String, Long> nodeTimeOffsets

Methods:
  + synchronizeClocks(nodes)
  + getClusterTime() → long
  + getLocalTimeOffset() → long
  + periodicSync()
```

#### NTPClient (LAB 4)
**Purpose:** Sync with external time source

```
Methods:
  + getCurrentTime() → long
  + getSystemTimeOffsetMs() → long
  + syncWithNTP(ntpServer)
  + isTimeSynced() → boolean
```

#### MessageOrderer
**Purpose:** Ensure causal message ordering

```
Data:
  - long lamportClock = 0
  - Map<String, integer[]> vectorClocks

Methods:
  + assignTimestamp(msg, origin)      // Lamport or vector
  + orderMessages(msgList) → List<Message>
  + getLogicalClock() → long
  + incrementClock()
```

---

### 3.4 CONSENSUS MODULE (Member 3 - Part A: Raft)

#### LogEntry
**Purpose:** Single entry in Raft log

```
Data:
  - int term               // Election term when created
  - int index              // Position in log
  - Message data           // The message content
  - boolean isCommitted    // Applied to state machine

Methods:
  + equals(other) → boolean
  + compareTo(other) → int
  + serialize() → byte[]
  + deserialize(bytes) → LogEntry
```

#### RaftConsensus
**Purpose:** Core Raft algorithm implementation

```
Data:
  - RaftState state (FOLLOWER, CANDIDATE, LEADER)
  - int currentTerm = 0
  - String votedFor = null
  - List<LogEntry> log
  - int commitIndex = 0
  - int lastApplied = 0
  - (Leader only) int[] nextIndex, int[] matchIndex

Methods:
  + appendEntry(entry) → boolean
  + commitEntry(entry)
  + getCurrentTerm() → int
  + getCurrentLeader() → String
  + replicate() → boolean
  + onAppendEntriesRPC(...)
  + onRequestVoteRPC(...)
  + tickElection()
  + tickHeartbeat()
```

#### ConsensusManager
**Purpose:** Coordinate Raft operations

```
Methods:
  + handleRPC(rpc)
  + applyEntries()
  + getCommitIndex() → int
  + onTermChange(newTerm)
```

---

### 3.5 CLIENT & MESSAGE (Member 3 - Part B)

#### Message
**Purpose:** Serializable message object

```
Data:
  - String messageId (UUID)
  - String senderId
  - String content
  - long physicalTimestamp
  - long logicalClock
  - int[] vectorClock

Methods:
  + toJSON() → String
  + fromJSON(json) → Message
  + equals(obj) → boolean
  + hashCode() → int
  + toString() → String
```

#### MessagingClient
**Purpose:** Client interface to cluster

```
Data:
  - Socket connection
  - String clusterId
  - Queue<Message> receivedMessages

Methods:
  + connect(host, port)
  + sendMessage(content) → Message
  + receiveMessage(timeoutMs) → Message
  + disconnect()
  + isConnected() → boolean
```

---

## 4. DATA FLOW

### 4.1 Message Send Flow
```
Client.sendMessage("Hello")
    ↓ Create Message(senderId, "Hello")
    ↓ Assign timestamps (physical, logical, vector)
    ↓ Serialize to JSON
    ↓ Send to ServerNode (leader)
    ↓
[LEADER SIDE]
    ↓ FailureDetector validates sender
    ↓ MessageDeduplicator checks if new
    ↓ RaftConsensus.appendEntry()
    ↓ ReplicationManager selects replicas (quorum)
    ↓ MessageReplicator sends to each replica
    ↓ Wait for ACKs from > N/2 nodes
    ↓ ConsensusManager.applyEntries()
    ↓ State machine applies
    ↓ ACK to client
```

### 4.2 Message Receive Flow (on Follower)
```
Network receives bytes
    ↓ ServerNode.handleIncomingMessage()
    ↓ Deserialize from JSON → Message
    ↓ Store in local Raft log
    ↓ Wait for commit notification from leader
    ↓ RaftConsensus gets commit index
    ↓ Apply entries[lastApplied+1..commitIndex]
    ↓ Store in state machine
    ↓ Client can now read via getReplica API
```

### 4.3 Leader Election Flow
```
Node timeout (no heartbeat for 5 seconds)
    ↓ RaftConsensus switches to CANDIDATE
    ↓ Increment currentTerm
    ↓ Vote for self
    ↓ LeaderElection.startElection(candidates)
    ↓ Send RequestVote RPC to all
    ↓ Collect votes from > N/2 nodes
    ↓ Become LEADER
    ↓ Start sending heartbeats
    ↓ FailureDetector notes new leader
```

### 4.4 Failure Detection Flow
```
Node X healthy
    ↓ Sends heartbeats every 2 seconds
    ↓ FailureDetector.onHeartbeatReceived()
    ↓ lastHeartbeatTime[X] = now
    ↓ failureCount[X] = 0
    ↓ failedNodes.remove(X)
    ↓
[5 seconds pass - no heartbeat]
    ↓ timeSince=5000ms > TIMEOUT=5000
    ↓ FailureDetector.onHeartbeatMissed()
    ↓ failureCount[X]++
    ↓
[10 seconds pass - 3 total misses]
    ↓ failureCount[X] >= MAX_FAILURES=3
    ↓ failedNodes.add(X)
    ↓ If X is leader: LeaderElection.triggerReelection()
    ↓ New leader elected within 5 seconds
```

---

## 5. THREAD SAFETY & CONCURRENCY

### 5.1 Shared Data Structures

| Data | Protection | Why |
|------|-----------|-----|
| nodes | ConcurrentHashMap | Multi-thread access |
| lastHeartbeatTime | ConcurrentHashMap | Read/write in HB task |
| currentLeader | volatile + lock | Atomic visibility + updates |
| currentTerm | volatile + lock | Atomic visibility + updates |
| log | ConcurrentLinkedList | Append from multiple threads |
| commitIndex | volatile | Broadcast from leader |
| failedNodes | ConcurrentHashSet | Add/remove asynchronously |

### 5.2 Synchronization Patterns

```java
// Election lock (for multi-step atomic operation)
electionLock.lock();
try {
    currentTerm++;
    currentLeader = newLeader;
    electionComplete.signalAll();
} finally {
    electionLock.unlock();
}

// Message dedup (fast check, slow add)
if (!dedup.isDuplicate(msgId)) {              // CAS check
    synchronized(dedup) {
        if (!dedup.isDuplicate(msgId)) {      // Double-check
            dedup.addMessageId(msgId);
        }
    }
}

// Replication wait (latch pattern)
CountDownLatch ackLatch = new CountDownLatch(quorumSize);
onAck((msgId, nodeId) -> ackLatch.countDown());
boolean success = ackLatch.await(5, TimeUnit.SECONDS);
```

### 5.3 Deadlock Prevention

**Rules:**
1. Always acquire locks in same order (election → replication → consensus)
2. Never hold multiple locks
3. Use `tryLock` with timeout, not indefinite `lock`
4. Never call blocking methods while holding lock
5. Use non-blocking collections (ConcurrentHashMap) when possible

---

## 6. TESTING STRATEGY

### 6.1 Unit Tests (Member 4)
```
Each module has corresponding test:
    MessagingServerTest, ServerNodeTest, LeaderElectionTest, 
    FailureDetectorTest, ReplicationManagerTest, ConsistencyHandlerTest,
    RaftConsensusTest, ConsensusManagerTest, LogEntryTest
    
Coverage Goal: > 85%
```

### 6.2 Integration Tests
```
IntegrationTest
    • Start 3-node cluster
    • Send message → verify on all nodes
    • Verify quorum replication
    • Verify consistency

FailureScenarioTest
    • Kill node 1 → others should continue
    • Kill leader → re-election happens
    • Stop then restart node → rejoin cluster
    • Network partition (2 vs 1)

PerformanceTest
    • Throughput: > 1000 msg/sec
    • Latency: < 100ms commit
    • Memory: < 1KB per message
```

---

## 7. ERROR HANDLING & RECOVERY

### 7.1 Node Failures
```
Leader dies:
    → FailureDetector sees no heartbeat > 10 sec
    → Triggers LeaderElection.triggerReelection()
    → Other nodes form new election
    → One becomes new leader
    → Resume normal operations

Follower dies:
    → FailureDetector detects after 10 seconds
    → Remove from replicator targets
    → Continue with N-1 nodes
    → When rejoins: recovery via log replication
```

### 7.2 Network Issues
```
Timeout in replication:
    → MessageReplicator timeout after 5 sec
    → Retry with exponential backoff
    → After 3 retries: treat node as failed

Message loss (if supported):
    → Message not seen by quorum
    → Return error to client
    → Client retries
    → MessageDeduplicator prevents double-process
```

### 7.3 Consistency Recovery
```
Follower lag:
    → Leader replays log entries
    → Follower catches up
    → Re-join quorum

Split brain prevention:
    → Majority can elect new leader
    → Minority cannot (can't get quorum)
    → When partition heals: minority's log overwritten
```

---

## 8. PERFORMANCE & SCALABILITY

### 8.1 Performance Targets

| Metric | Target | Implementation |
|--------|--------|-----------------|
| Throughput | 1000+ msg/sec | Batch + parallel replication |
| Latency | < 100ms | Pipelined replication |
| Election time | 3-5 seconds | Fixed election timeout |
| Failure detection | < 10 sec | 3 × 2sec heartbeats + timeout |
| Memory/node | < 100MB | Bounded log size |
| Memory/message | < 1KB | Efficient JSON serialization |

### 8.2 Scalability

**What scales well:**
- Number of messages (queue based)
- Replication factor (N replicas)
- Message throughput (parallel processing)

**What doesn't scale:**
- Number of nodes in quorum writes (must wait for 51%+)
- Consensus decision time (consensus is sequential)

**Recommendation:** 3-5 nodes optimal for this project

---

## 9. DEPLOYMENT & OPERATIONS

### 9.1 Starting Development Cluster
```bash
# Terminal 1: Node A (will become leader)
java -Dnode.id=node-a -Dnode.port=10001 MessagingServer

# Terminal 2: Node B  
java -Dnode.id=node-b -Dnode.port=10002 MessagingServer

# Terminal 3: Node C
java -Dnode.id=node-c -Dnode.port=10003 MessagingServer

# Terminal 4: Client
java MessagingClient --server node-a:10001
```

### 9.2 Monitoring

```
Health Checks:
  • Is leader responding to heartbeats?
  • Are followers replicating?
  • Are clocks synchronized?
  • Any failed nodes?

Metrics to Watch:
  • Message throughput
  • Replication latency
  • Failed nodes count
  • Clock skew
  • CPU & memory usage
```

---

## 10. NEXT STEPS & MILESTONES

**Days 1-2:** Design Review
- [ ] All members understand this architecture
- [ ] Any questions resolved
- [ ] Package structure confirmed

**Days 3-5:** Skeleton Implementation
- [ ] All classes created with empty methods
- [ ] All test files created (empty)
- [ ] Can compile with `mvn clean compile`

**Days 6-8:** Core Implementation
- [ ] All methods implemented
- [ ] Unit tests written and passing
- [ ] > 85% code coverage

**Day 9:** Integration
- [ ] 3-node cluster starts without errors
- [ ] Messages flow end-to-end
- [ ] All modules communicate

**Days 10-11:** Testing & Fixes
- [ ] Failure tests pass
- [ ] Performance benchmarks
- [ ] Bug fixes

**Day 12:** Finalization
- [ ] Final demo
- [ ] Documentation complete
- [ ] Submission ready

---

**Architecture Complete & Ready for Development! 🚀**

|-----------|-----------------|-------|
| **Core Framework** | System initialization, thread management | Member 1 |
| **Synchronization** | Locks, semaphores, barriers | Member 1 |
| **Data Structures** | Custom DS, algorithms | Member 2 |
| **Server/Node** | Request handling, state management | Member 3 |
| **Communication** | Message passing, RPC | Member 3 |
| **Testing** | Unit & integration tests | Member 4 |

---

## 3. DETAILED COMPONENT DESIGN

### 3.1 Core Framework
**[TO BE FILLED]**

**Key Classes:**
- `DistributedSystem.java` - Main system orchestrator
- `Node.java` - Individual node implementation
- `Message.java` - Message structure

### 3.2 Synchronization Layer
**[TO BE FILLED]**

**Concurrency Mechanisms:**
- Thread-safe operations
- Synchronization primitives used

### 3.3 Data Structures & Algorithms
**[TO BE FILLED]**

**Algorithms to Implement:**
- Algorithm 1: [Name & description]
- Algorithm 2: [Name & description]

### 3.4 Server/Node Implementation
**[TO BE FILLED]**

**Responsibilities:**
- Request processing
- State management
- Response generation

### 3.5 Communication Protocol
**[TO BE FILLED]**

**Message Format:**
```json
{
  "messageId": "UUID",
  "timestamp": "ISO-8601",
  "type": "REQUEST|RESPONSE|HEARTBEAT",
  "payload": {}
}
```

---

## 4. DATA STRUCTURES

### 4.1 Key Data Structures
```java
// Example
class Node {
    String nodeId;
    State currentState;
    Queue<Message> incomingMessages;
    // ... other attributes
}
```

---

## 5. ALGORITHMS

### 5.1 [Algorithm Name]
**Purpose:** [What does it do]

**Pseudocode:**
```
ALGORITHM [Name]
BEGIN
    // TO BE FILLED
END
```

**Complexity:** O(?) Time, O(?) Space

---

## 6. THREAD SAFETY & CONCURRENCY

### 6.1 Critical Sections
- [Identify critical sections that need synchronization]

### 6.2 Synchronization Strategy
- **Locks Used:** ReentrantLock, Synchronized methods, etc.
- **Thread Pools:** Executor services, custom thread pools
- **Message Queues:** BlockingQueue, custom queues

---

## 7. ERROR HANDLING

### 7.1 Exception Hierarchy
```
DistributedSystemException (extends Exception)
├── NetworkException
├── SynchronizationException
├── StateException
└── ConfigurationException
```

### 7.2 Failure Scenarios
- Network failures
- Node crashes
- Deadlock scenarios
- Recovery mechanisms

---

## 8. TESTING STRATEGY

### 8.1 Unit Tests
- [List unit test categories]

### 8.2 Integration Tests
- [List integration test scenarios]

### 8.3 Performance Tests
- [List performance benchmarks]

---

## 9. CONFIGURATION

### 9.1 Configuration Parameters
```properties
# Example
system.timeout=5000
system.threads=10
node.heartbeatInterval=1000
```

---

## 10. TIMELINE & MILESTONES

- **Day 1-2:** Requirements Finalization
- **Day 3-6:** Core Development
- **Day 7-9:** Integration & Testing
- **Day 10-12:** Documentation & Submission

---

## 11. RISKS & MITIGATION

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Complexity of synchronization | High | Early POC, thorough testing |
| Integration issues | High | Frequent merges, clear interfaces |
| Time management | Medium | Strict timeline adherence |

---

## APPENDIX

### Diagrams to Add
- [ ] Architecture diagram
- [ ] Sequence diagram for key scenarios
- [ ] State machine diagram
- [ ] Class diagram

### References
- [Reference materials from labs 5 & 6]
- [Additional DS textbooks/resources]

---

**Document Prepared By:** [Team Members]  
**Last Updated:** March 20, 2026  
**Next Review:** [Date]
