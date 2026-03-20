# DISTRIBUTED MESSAGING - MEMBER ASSIGNMENTS

**Project:** Distributed Messaging System  
**Team:** 4 Members  
**Duration:** 10-12 Days  
**Package:** `com.ds.messaging`

---

## 📊 MODULE DISTRIBUTION TO MEMBERS

```
MODULE                          MEMBER          PACKAGE
────────────────────────────────────────────────────────────────
1. Server & Node Mgmt          Member 1        server/
2. Failure Detection           Member 1        server/
3. Replication & Consistency   Member 2        replication/
4. Time Sync & Message Order   Member 2        time/
5. Raft Consensus              Member 3        consensus/
6. Client & Messages           Member 3        client/
7. Utils, Config, Tests        Member 4        utils/, all test/
```

---

## 👤 MEMBER 1: SERVER INFRASTRUCTURE & FAILURE DETECTION

### Responsibility
Build the core server architecture with node management and failure detection

### Files to Implement

#### 1. **src/main/java/com/ds/messaging/server/MessagingServer.java**
```java
package com.ds.messaging.server;

public class MessagingServer {
    // Server initialization and lifecycle
    // Manage cluster of nodes
    // Accept client connections
    
    public MessagingServer(Config config) { }
    public void start() { }
    public void stop() { }
    public void addNode(String nodeId, String host, int port) { }
    public ServerNode getNode(String nodeId) { }
    public List<ServerNode> getAllNodes() { }
}
```

#### 2. **src/main/java/com/ds/messaging/server/ServerNode.java**
```java
package com.ds.messaging.server;

public class ServerNode {
    // Individual node in the cluster
    // Node state and lifecycle
    // Connection to other nodes
    
    private String nodeId;
    private String host;
    private int port;
    private NodeState state;
    
    public ServerNode(String id, String host, int port) { }
    public void initialize() { }
    public void connect() { }
    public boolean isHealthy() { }
    public void sendToNode(String targetId, Message msg) { }
    public void shutdown() { }
}
```

#### 3. **src/main/java/com/ds/messaging/server/LeaderElection.java**
```java
package com.ds.messaging.server;

// From LAB 6 concepts
public class LeaderElection {
    // Elect a coordinator/leader node
    // Ensure only one leader at a time
    
    public void startElection(List<ServerNode> candidates) { }
    public ServerNode getCurrentLeader() { }
    public boolean isLeaderAlive() { }
    public void triggerReelection() { }
    public void setLeader(ServerNode leader) { }
}
```

#### 4. **src/main/java/com/ds/messaging/server/FailureDetector.java**
```java
package com.ds.messaging.server;

// Heartbeat-based failure detection
public class FailureDetector {
    // Send heartbeats to monitor node health
    // Detect failed nodes
    // Report failures to leader election
    
    public void startHeartbeat(ServerNode node) { }
    public void stopHeartbeat(String nodeId) { }
    public boolean isNodeAlive(String nodeId) { }
    public List<String> getFailedNodes() { }
    public void onHeartbeatMissed(String nodeId) { }
}
```

#### 5. **src/main/java/com/ds/messaging/server/NodeState.java**
```java
package com.ds.messaging.server;

public enum NodeState {
    STARTING,
    READY,
    SYNCING,
    UNHEALTHY,
    SHUTTING_DOWN,
    DEAD
}
```

### Test Files to Create
```
src/test/java/com/ds/messaging/server/
├── MessagingServerTest.java
├── ServerNodeTest.java
├── LeaderElectionTest.java
└── FailureDetectorTest.java
```

### Success Criteria
- ✅ Server can start/stop cleanly
- ✅ Nodes register and connect
- ✅ Leader election works with 3-5 nodes
- ✅ Heartbeat detects failed nodes within 5 seconds
- ✅ >85% unit test coverage
- ✅ No resource leaks (threads, connections)

### Integration Points
- **With Member 2:** Send replication commands to nodes
- **With Member 3:** Notify about leader changes, consensus updates
- **With Member 4:** Provide node status for test scenarios

---

## 👤 MEMBER 2: REPLICATION & MESSAGE CONSISTENCY

### Responsibility
Implement message replication across nodes and ensure consistency

### Files to Implement

#### 1. **src/main/java/com/ds/messaging/replication/ReplicationManager.java**
```java
package com.ds.messaging.replication;

// From LAB 5 concepts
public class ReplicationManager {
    // Manage replication across cluster
    // Choose replication strategy (quorum, all, etc.)
    // Track replication status
    
    public void replicateMessage(Message msg, List<ServerNode> replicas) { }
    public void confirmReplication(String messageId, String nodeId) { }
    public boolean isMessageReplicated(String messageId) { }
    public int getReplicationCount(String messageId) { }
}
```

#### 2. **src/main/java/com/ds/messaging/replication/ConsistencyHandler.java**
```java
package com.ds.messaging.replication;

// Quorum-based consistency guarantees
public class ConsistencyHandler {
    // Implement consistency model
    // Manage replicas for quorum reads/writes
    
    public boolean writeQuorum(String messageId, int quorumSize) { }
    public Message readQuorum(String messageId, List<ServerNode> nodes) { }
    public void ensureConsistency(String messageId) { }
    public boolean checkConvergence() { }
}
```

#### 3. **src/main/java/com/ds/messaging/replication/MessageReplicator.java**
```java
package com.ds.messaging.replication;

// Per-message replication logic
public class MessageReplicator {
    // Handle replication of individual messages
    // Retry failed replications
    // Track replication timeline
    
    public void replicate(Message msg, ServerNode target) { }
    public boolean waitForAck(String messageId, int timeoutMs) { }
    public void retryReplication(String messageId) { }
    public void onReplicationFail(String messageId, String nodeId) { }
}
```

#### 4. **src/main/java/com/ds/messaging/replication/MessageDeduplicator.java**
```java
package com.ds.messaging.replication;

// Handle duplicate messages in cluster
public class MessageDeduplicator {
    // Track message IDs already seen
    // Prevent reprocessing duplicates
    // Cleanup old dedup records
    
    public boolean isDuplicate(String messageId) { }
    public void addMessageId(String messageId) { }
    public void removeOldMessages(long retentionMs) { }
    public void clearCache() { }
}
```

### Test Files to Create
```
src/test/java/com/ds/messaging/replication/
├── ReplicationManagerTest.java
├── ConsistencyHandlerTest.java
├── MessageReplicatorTest.java
└── MessageDeduplicatorTest.java
```

### Success Criteria
- ✅ Messages replicate to all nodes within 100ms
- ✅ Quorum consistency works correctly
- ✅ Duplicates properly detected and eliminated
- ✅ Replication survives node failures
- ✅ >85% test coverage
- ✅ No message loss in happy path

### Integration Points
- **With Member 1:** Use ServerNode for replication targets
- **With Member 3:** Coordinate with consensus before committing
- **With Member 4:** Provide replication status for tests

---

## 👤 MEMBER 3: TIME SYNCHRONIZATION, ORDERING & CONSENSUS

### Responsibility
Implement clock synchronization, message ordering, and Raft consensus

### Files to Implement

#### 1. **src/main/java/com/ds/messaging/time/NTPClient.java**
```java
package com.ds.messaging.time;

// From LAB 4 concepts - Network Time Protocol
public class NTPClient {
    // Synchronize local clock with NTP server
    // Get accurate system time
    
    public long getCurrentTime() { }
    public long getSystemTimeOffsetMs() { }
    public void syncWithNTP(String ntpServer) { }
    public boolean isTimeSynced() { }
}
```

#### 2. **src/main/java/com/ds/messaging/time/ClockSynchronizer.java**
```java
package com.ds.messaging.time;

// Synchronize clocks across cluster nodes
public class ClockSynchronizer {
    // Run Berkeley algorithm or similar
    // Maintain time consensus
    // Handle clock skew
    
    public void synchronizeClocks(List<ServerNode> nodes) { }
    public long getClusterTime() { }
    public long getLocalTimeOffset() { }
    public void periodicSync() { }
}
```

#### 3. **src/main/java/com/ds/messaging/time/MessageOrderer.java**
```java
package com.ds.messaging.time;

// Order messages by logical or physical time
public class MessageOrderer {
    // Use vector clocks or Lamport clocks
    // Reorder out-of-order messages
    // Detect causal relationships
    
    public void assignTimestamp(Message msg, ServerNode origin) { }
    public List<Message> orderMessages(List<Message> msgList) { }
    public long getLogicalClock() { }
    public void incrementClock() { }
}
```

#### 4. **src/main/java/com/ds/messaging/consensus/RaftConsensus.java**
```java
package com.ds.messaging.consensus;

// Raft consensus algorithm
public class RaftConsensus {
    // Three states: Follower, Candidate, Leader
    // Log replication
    // Safety and liveness guarantees
    
    public RaftConsensus(String nodeId, List<String> peerIds) { }
    public void appendEntry(LogEntry entry) { }
    public void commitEntry(LogEntry entry) { }
    public int getCurrentTerm() { }
    public String getCurrentLeader() { }
    public boolean replicate() { }
}
```

#### 5. **src/main/java/com/ds/messaging/consensus/LogEntry.java**
```java
package com.ds.messaging.consensus;

// Individual Raft log entry
public class LogEntry {
    private int term;
    private int index;
    private Message data;
    private boolean isCommitted;
    
    public LogEntry(int term, int index, Message data) { }
    public boolean equals(LogEntry other) { }
}
```

#### 6. **src/main/java/com/ds/messaging/consensus/ConsensusManager.java**
```java
package com.ds.messaging.consensus;

// Coordinate consensus operations
public class ConsensusManager {
    // Manage consensus state machine
    // Handle term changes
    // Manage leader election within Raft
    
    public void handleRPC(RPC rpc) { }
    public void applyEntries() { }
    public int getCommitIndex() { }
    public void onTermChange(int newTerm) { }
}
```

#### 7. **src/main/java/com/ds/messaging/client/Message.java**
```java
package com.ds.messaging.client;

// Message object that gets replicated and ordered
public class Message {
    private String messageId;
    private String senderId;
    private String content;
    private long timestamp;
    private long logicalClock;
    private int vector_clock[];
    
    public Message(String senderId, String content) { }
    public String toJSON() { }
    public static Message fromJSON(String json) { }
    public boolean equals(Message other) { }
}
```

### Test Files to Create
```
src/test/java/com/ds/messaging/consensus/
├── RaftConsensusTest.java
├── ConsensusManagerTest.java
└── LogEntryTest.java
```

### Success Criteria
- ✅ Raft consensus elects leaders correctly
- ✅ Log entries replicated to majority (2 of 3+)
- ✅ Clock sync keeps skew < 10ms
- ✅ Messages ordered correctly by causality
- ✅ >85% test coverage
- ✅ Consensus tolerates 1 node failure (3-node cluster)

### Integration Points
- **With Member 1:** Integrate leader election with Raft
- **With Member 2:** Ensure replication respects consensus
- **With Member 4:** Provide consensus status to tests

---

## 👤 MEMBER 4: TESTING, CLIENT & UTILITIES

### Responsibility
Implement client interface, utilities, and comprehensive testing

### Files to Implement

#### 1. **src/main/java/com/ds/messaging/client/MessagingClient.java**
```java
package com.ds.messaging.client;

// Client application using the messaging system
public class MessagingClient {
    // Connect to messaging cluster
    // Send messages
    // Receive messages
    // Handle retries
    
    public MessagingClient(String clusterId) { }
    public void connect(String serverHost, int port) { }
    public void sendMessage(String content) { }
    public Message receiveMessage(long timeoutMs) { }
    public void disconnect() { }
}
```

#### 2. **src/main/java/com/ds/messaging/utils/Config.java**
```java
package com.ds.messaging.utils;

// Configuration for the system
public class Config {
    // Node configuration
    // Cluster configuration
    // Timeout parameters
    
    private int nodeCount;
    private int port;
    private long heartbeatIntervalMs;
    private long electionTimeoutMs;
    private int replicationFactor;
    
    public Config loadFromFile(String path) { }
    public void saveToFile(String path) { }
}
```

#### 3. **src/main/java/com/ds/messaging/utils/Logger.java**
```java
package com.ds.messaging.utils;

// Utility for structured logging (wraps SLF4J)
public class Logger {
    public static void info(String msg, Object... args) { }
    public static void error(String msg, Throwable e) { }
    public static void debug(String msg) { }
    public static void warn(String msg) { }
}
```

#### 4. **Comprehensive Test Files**
```
src/test/java/com/ds/messaging/
├── server/
│   ├── MessagingServerTest.java
│   ├── ServerNodeTest.java
│   ├── LeaderElectionTest.java
│   └── FailureDetectorTest.java
├── replication/
│   ├── ReplicationManagerTest.java
│   ├── ConsistencyHandlerTest.java
│   ├── MessageReplicatorTest.java
│   └── MessageDeduplicatorTest.java
├── consensus/
│   ├── RaftConsensusTest.java
│   ├── ConsensusManagerTest.java
│   └── LogEntryTest.java
├── IntegrationTest.java          (multi-node, end-to-end)
├── FailureScenarioTest.java      (simulate node failures)
└── PerformanceTest.java          (throughput, latency benchmarks)
```

#### 5. **Integration & System Tests**

**src/test/java/com/ds/messaging/IntegrationTest.java**
```java
// Test full system with 3+ nodes
// Verify messages flow correctly
// Verify replication works
// Verify consistency is maintained
```

**src/test/java/com/ds/messaging/FailureScenarioTest.java**
```java
// Test node failures
// Test network partitions
// Test recovery
// Test message loss prevention
```

**src/test/java/com/ds/messaging/PerformanceTest.java**
```java
// Measure throughput (msg/sec)
// Measure latency (time to replicate)
// Measure consensus latency
// Load test with concurrent clients
```

### Test Coverage Goals
```
- Unit tests: >85% coverage for each module
- Integration tests: All major workflows
- Failure tests: Node failures, network issues
- Performance baseline: < 100ms replication, > 1000 msg/sec
```

### Test Execution
```bash
# Run all tests
mvn clean test

# Run with coverage report
mvn test jacoco:report

# Run specific test
mvn test -Dtest=IntegrationTest

# Run integration tests only
mvn test -Dgroups=integration
```

### Success Criteria
- ✅ MessagingClient can send/receive messages
- ✅ All unit tests pass (>85% coverage)
- ✅ Integration test passes with 3-node cluster
- ✅ Failure scenarios handled gracefully
- ✅ Performance meets baseline
- ✅ No resource leaks
- ✅ Config loading works correctly

### Deliverables
- MessagingClient.java
- Config.java
- Logger.java
- All test files
- Test documentation
- Performance benchmark results

### Integration Points
- **With All Members:** Create tests for their modules
- **Create mock versions** of other modules if needed during test development
- **Run daily integration** to ensure all modules work together

---

## 📅 TIMELINE & MILESTONES

### Day 1-2: Design & Setup
```
[ ] All: Review requirements
[ ] All: Design interfaces and contracts
[ ] M1: Design server architecture
[ ] M2: Design replication protocol
[ ] M3: Design consensus and ordering
[ ] M4: Design test strategy
```

### Day 3-5: Skeleton Implementation
```
[ ] M1: Create all server classes (empty methods)
[ ] M2: Create all replication classes
[ ] M3: Create consensus and time sync classes
[ ] M4: Create utility and client classes
[ ] All: Create all test file stubs
```

### Day 6-8: Core Implementation
```
[ ] M1: Implement server lifecycle, node connection
[ ] M2: Implement basic replication
[ ] M3: Implement Raft consensus
[ ] M4: Unit tests pass for all modules
[ ] All: Daily git commits and merges
```

### Day 9: Integration
```
[ ] All: 3-node cluster runs without crashes
[ ] M1+M2: Replication works through server
[ ] M2+M3: Consensus integrated
[ ] M4: Integration test passes
```

### Day 10-11: Testing & Optimization
```
[ ] M4: Run failure scenario tests
[ ] All: Performance tests and optimization
[ ] All: Documentation finalized
[ ] All: Code review round
```

### Day 12: Finalization
```
[ ] All: Final integration test
[ ] All: Demo preparation
[ ] All: No build warnings
[ ] All: Final documentation review
[ ] Submit final JAR and docs
```

---

## 🎯 QUALITY GATES

### Before Each Commit
```
[ ] Code compiles without warnings: mvn clean compile
[ ] Your unit tests pass: mvn test -Dtest=YourClass
[ ] No new TODO comments added
[ ] Code follows Java conventions
```

### Before Daily Integration
```
[ ] All tests in your module pass
[ ] Integration tests pass (via member 4)
[ ] Documentation updated
[ ] No hardcoded values or debug output
```

### Before Final Submission
```
[ ] Full build succeeds: mvn clean verify
[ ] >85% test coverage overall
[ ] No compilation warnings
[ ] All documentation complete
[ ] Demo runs without errors
[ ] README.md updated with instructions
```

---

## 📞 COMMUNICATION

### Daily Standup (10:00 AM - 15 minutes)
```
What did I complete yesterday?
What am I working on today?
What blockers do I have?
```

### Daily Integration
```
[ ] Push to your feature branch
[ ] Create pull request for review
[ ] Wait for approval from member 4 (testing)
[ ] Merge to main branch
```

### Risk Escalation
```
If blocked > 30 minutes:
1. Ask team in daily standup
2. Pair program with relevant member
3. Escalate to teacher if architectural issue
```

---

## ✅ FINAL CHECKLIST

### All Members
- [ ] Git repository set up with branches
- [ ] Maven builds successfully
- [ ] IDE set up (VS Code / IntelliJ)
- [ ] Read all documentation
- [ ] Understand your 3 files to implement

### Each Member (Before Day 3)
- [ ] Create skeleton/stub code
- [ ] Create corresponding test files
- [ ] Push to feature branch
- [ ] Request review from Member 4

### Each Member (Before Day 6)
- [ ] Core implementation complete
- [ ] Unit tests passing
- [ ] Documentation comments added
- [ ] Daily commits to Git

### Each Member (Before Day 9)
- [ ] Full testing with other modules
- [ ] Integration working
- [ ] No resource leaks
- [ ] Performance acceptable

---

**Ready to build a world-class distributed messaging system! 🚀**

