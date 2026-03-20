# MEMBER TASK ASSIGNMENT & CHECKLIST

**Project:** Distributed Systems - SLIIT  
**Team:** 4 Members  
**Duration:** 10-12 Days

---

## MEMBER 1: CORE ARCHITECTURE & SYNCHRONIZATION

### Overview
Responsible for the foundational framework and thread-safe synchronization primitives

### Phase 1: Days 1-2 (Planning)
- [ ] Review QUICK_START.md
- [ ] Extract requirements from scenario3.pdf
- [ ] Attend design meeting
- [ ] Define synchronization interfaces
- [ ] Create skeleton code structure

### Phase 2: Days 3-6 (Implementation)

#### Files to Create:

**1. `src/main/java/com/ds/project/core/DistributedSystem.java`**
```java
public class DistributedSystem {
    // System-wide initialization
    // Node creation and management
    // Lifecycle management
    // Configuration management
}
```
**Default methods needed:**
- `initialize(Config config)`
- `createNode(String nodeId)`
- `getAllNodes()`
- `shutdown()`

**Tests:** `src/test/java/com/ds/project/core/DistributedSystemTest.java`

---

**2. `src/main/java/com/ds/project/core/ThreadPool.java`**
```java
public class ThreadPool {
    // Thread pool management
    // Task scheduling
    // Thread lifecycle
}
```
**Default methods needed:**
- `submit(Runnable task)`
- `shutdown()`
- `waitForTermination()`

**Tests:** `src/test/java/com/ds/project/core/ThreadPoolTest.java`

---

**3. `src/main/java/com/ds/project/core/SyncPrimitives.java`**
```java
public class Lock { /* DLock implementation */ }
public class Semaphore { /* Semaphore implementation */ }
public class Barrier { /* Barrier implementation */ }
```

**Tests:** `src/test/java/com/ds/project/core/SyncPrimitivesTest.java`

---

**4. `src/main/java/com/ds/project/core/Configuration.java`**
```java
public class Configuration {
    // Configuration container
    // Settings management
}
```

### Phase 3: Days 7-9 (Integration)
- [ ] Review Member 3's Node implementation
- [ ] Ensure synchronization works in Node context
- [ ] Update documentation
- [ ] Fix any integration issues

### Phase 4: Days 10-12 (Finalization)
- [ ] Code review by Member 4
- [ ] Final tests pass
- [ ] Documentation complete
- [ ] No warnings in build

### SUCCESS CRITERIA
- ✅ ThreadPool fully functional
- ✅ All sync primitives thread-safe
- ✅ >90% unit test coverage
- ✅ Zero deadlocks in testing
- ✅ Documentation complete

### DELIVERABLES
- ThreadPool.java
- SyncPrimitives.java
- DistributedSystem.java
- All unit tests
- Code documentation

---

## MEMBER 2: ALGORITHMS & DATA STRUCTURES

### Overview
Responsible for implementing algorithms and custom data structures

### Phase 1: Days 1-2 (Planning)
- [ ] Review QUICK_START.md
- [ ] Extract requirements from scenario3.pdf
- [ ] Attend design meeting
- [ ] Understand algorithms from scenario
- [ ] Define algorithm interfaces

### Phase 2: Days 3-6 (Implementation)

#### Files to Create:

**1. Algorithm Files (Based on Scenario)**
```java
// Create one file per algorithm
src/main/java/com/ds/project/algorithms/
├── Algorithm1.java    (implement)
├── Algorithm2.java    (implement)
└── Algorithm3.java    (implement)
```

**Example structure:**
```java
public class LeaderElection {
    public void electLeader(List<Node> nodes) { }
    public boolean isLeaderValid() { }
}
```

**Tests:** `src/test/java/com/ds/project/algorithms/AlgorithmTest.java`

---

**2. Data Structure Files**
```java
src/main/java/com/ds/project/datastructures/
├── CustomQueue.java
├── CustomTree.java
├── CustomGraph.java   // if needed
└── CustomList.java    // if needed
```

**Example:**
```java
public class CustomQueue<T> {
    public void enqueue(T element) { }
    public T dequeue() { }
    public boolean isEmpty() { }
}
```

**Tests:** `src/test/java/com/ds/project/datastructures/DataStructureTest.java`

---

**3. Utilities File**
```java
src/main/java/com/ds/project/utils/
├── Algorithms.java     (static utility methods)
├── DataStructureUtils.java
└── Utilities.java      (generic helpers)
```

### Phase 3: Days 7-9 (Integration)
- [ ] Review with Member 3 (how algorithms are used)
- [ ] Test with actual Node usage
- [ ] Performance optimization
- [ ] Documentation of algorithms

### Phase 4: Days 10-12 (Finalization)
- [ ] Code review
- [ ] All tests pass
- [ ] Performance acceptable
- [ ] Documentation complete

### SUCCESS CRITERIA
- ✅ All algorithms correct
- ✅ Data structures efficient
- ✅ >95% algorithm test coverage
- ✅ Performance meets requirements
- ✅ All tests passing

### DELIVERABLES
- All Algorithm files
- All DataStructure files
- Utilities file
- All unit tests
- Algorithm documentation

---

## MEMBER 3: SERVER/NODE & COMMUNICATION

### Overview
Responsible for implementing server nodes and communication layer

### Phase 1: Days 1-2 (Planning)
- [ ] Review QUICK_START.md
- [ ] Extract requirements from scenario3.pdf
- [ ] Attend design meeting
- [ ] Define server interfaces
- [ ] Plan message protocol

### Phase 2: Days 3-6 (Implementation)

#### Files to Create:

**1. `src/main/java/com/ds/project/core/Node.java`**
```java
public class Node implements IDistributedNode {
    // Node state management
    // Message sending/receiving
    // State machine
    // Request handling
}
```

**Default methods needed:**
- `initialize(NodeConfiguration config)`
- `sendMessage(String targetNode, Message message)`
- `receiveMessage()`
- `getState()`
- `shutdown()`

**Tests:** `src/test/java/com/ds/project/core/NodeTest.java`

---

**2. `src/main/java/com/ds/project/core/NodeState.java`**
```java
public enum NodeState {
    INITIALIZING,
    READY,
    PROCESSING,
    FAILED,
    SHUTDOWN
}
```

---

**3. `src/main/java/com/ds/project/communication/Message.java`**
```java
public class Message implements IMessage {
    // Message ID
    // Message type
    // Timestamp
    // Payload
    // Serialization
}
```

**Tests:** `src/test/java/com/ds/project/communication/MessageTest.java`

---

**4. `src/main/java/com/ds/project/communication/MessageHandler.java`**
```java
public class MessageHandler {
    // Handle incoming messages
    // Route messages
    // Process requests
}
```

**Tests:** `src/test/java/com/ds/project/communication/MessageHandlerTest.java`

---

**5. `src/main/java/com/ds/project/communication/MessageCodec.java`**
```java
public class MessageCodec {
    // Serialize messages (Message -> bytes/JSON)
    // Deserialize messages (bytes/JSON -> Message)
}
```

### Phase 3: Days 7-9 (Integration)
- [ ] Integrate with Member 1's synchronization
- [ ] Integrate with Member 2's algorithms
- [ ] Test message passing between nodes
- [ ] Test state consistency

### Phase 4: Days 10-12 (Finalization)
- [ ] Code review
- [ ] Integration tests pass
- [ ] Communication reliable
- [ ] Documentation complete

### SUCCESS CRITERIA
- ✅ Nodes communicate reliably
- ✅ Messages delivered in order
- ✅ State management correct
- ✅ >85% code coverage
- ✅ No deadlocks

### DELIVERABLES
- Node.java
- Message.java
- MessageHandler.java
- MessageCodec.java
- All communication files
- All integration tests
- Communication protocol documentation

---

## MEMBER 4: TESTING & DOCUMENTATION

### Overview
Responsible for comprehensive testing, quality assurance, and all documentation

### Phase 1: Days 1-2 (Planning)
- [ ] Review QUICK_START.md and all documentation
- [ ] Extract requirements from scenario3.pdf
- [ ] Attend design meeting
- [ ] Create test plan
- [ ] Set up test infrastructure

### Phase 2: Days 3-6 (Test Development)

#### Files to Create:

**1. UNIT TEST TEMPLATE** - Create for each component
```java
// In src/test/java/com/ds/project/[package]/
public class ComponentTest {
    @Before public void setUp() { }
    @After public void tearDown() { }
    @Test public void testSuccess() { }
    @Test public void testFailure() { }
}
```

**Tests needed:**
- `core/DistributedSystemTest.java`
- `core/ThreadPoolTest.java`
- `core/SyncPrimitivesTest.java`
- `algorithms/AlgorithmTest.java`
- `datastructures/DataStructureTest.java`
- `communication/MessageTest.java`
- `core/NodeTest.java`

---

**2. INTEGRATION TESTS**
```java
src/test/java/com/ds/project/integration/
├── IntegrationTest.java         (multi-node scenarios)
├── EndToEndTest.java            (full workflow)
├── CommunicationTest.java       (message delivery)
└── PerformanceTest.java         (load/stress)
```

---

### Phase 3: Days 7-9 (Documentation)

#### Documentation Files to Complete/Create:

**1. Update `docs/DESIGN.md`**
- [ ] Fill in all [TO BE FILLED] sections
- [ ] Add architecture diagrams
- [ ] Document decision rationale
- [ ] Add complexity analysis

**2. Update `docs/API.md`**
- [ ] Complete all interface definitions
- [ ] Add method signatures
- [ ] Add usage examples
- [ ] Document exceptions

**3. Update `docs/TESTING.md`**
- [ ] Add test cases for each component
- [ ] Document test coverage
- [ ] Add performance benchmarks
- [ ] Document test results

**4. Create `docs/INTERFACES.md`**
```markdown
# Interface Contracts

## IDistributedNode
- Pre-conditions
- Post-conditions
- Throws
- Examples

## IMessage
- Pre-conditions
- Post-conditions
- ...
```

**5. Create `docs/DECISIONS.md`**
```markdown
## Design Decision 001: Synchronization Strategy
Date: [date]
Status: [approved/pending]
Rationale: [explanation]
Owner: [member name]
```

**6. Create `docs/README_IMPLEMENTATION.md`**
```markdown
# Implementation Guide

## How to Build
## How to Run
## How to Test
## Known Issues
## Future Improvements
```

**7. Update main `README.md` if needed**

### Phase 4: Days 10-12 (Demo & Submission)

#### Additional Files:

**1. Create `docs/DEMO.md`**
```markdown
# Demo Script

## Setup Steps
1. [step]
2. [step]

## Demo Scenarios
1. Basic operation
2. Error handling
3. Performance

## Expected Output
```

**2. Create sample scripts/configs**
- Configuration examples
- Sample input files (if needed)
- Running instructions

### SUCCESS CRITERIA
- ✅ >80% code coverage
- ✅ All tests passing
- ✅ All documentation complete
- ✅ Demo working perfectly
- ✅ No compilation warnings

### DELIVERABLES
- All test files
- All documentation files
- Test coverage report
- Demo script
- README for testers

---

## SHARED BY ALL MEMBERS

### Daily Responsibilities
- [ ] 10:00 AM: Standup meeting
- [ ] Commit code daily
- [ ] Update shared tracking spreadsheet
- [ ] Review PRs from teammates
- [ ] Test integration with other components

### Review Checklist (Before PR merge)
- [ ] Unit tests pass locally
- [ ] Code compiles without warnings
- [ ] JavaDoc complete for public methods
- [ ] No hardcoded values
- [ ] Proper error handling
- [ ] Logging at appropriate levels
- [ ] Code reviewed by at least 1 member

---

## INTEGRATION POINTS (Know about these!)

**Member 1 ↔ Member 3:**
- ThreadPool/Sync used in Node

**Member 2 ↔ Member 3:**
- Algorithms used in message handlers

**Member 3 ↔ Member 2:**
- Data structures used in messages

**Everyone ↔ Member 4:**
- Providing tests and documentation

---

## WEEKLY MILESTONES

### End of Day 2
- [ ] All design docs filled
- [ ] Git repo ready
- [ ] Skeleton code created
- [ ] First meeting completed

### End of Day 6
- [ ] All components have implementations
- [ ] Unit tests >70% coverage
- [ ] Basic integration working
- [ ] Any blockers resolved

### End of Day 9
- [ ] Full system integrated
- [ ] All tests passing
- [ ] Performance acceptable
- [ ] Docs 80% complete

### End of Day 12
- [ ] Final submission ready
- [ ] All tests passing
- [ ] Docs 100% complete
- [ ] Demo working

---

## SUPPORT RESOURCES

### When Stuck:
1. Check QUICK_START.md
2. Review relevant documentation file
3. Ask in daily standup
4. Pair program with teammate
5. Research online documentation

### Git Issues:
- Commit too early? → Create new branch
- Merge conflict? → Resolve together
- Wrong branch? → Cherry-pick or revert

### Build Issues:
```bash
mvn clean compile     # Clear and rebuild
mvn dependency:tree   # Check dependencies
mvn -X test          # Debug output
```

---

**Remember:** Communicate early, test often, commit daily!

Good luck! 🚀
