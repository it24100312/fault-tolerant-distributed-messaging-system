# 🔴 MEMBER 1: INTEGRATION DIAGRAM & API CONTRACTS

**Visual map of who calls your code and when**

---

## 🏗️ ARCHITECTURE FLOW: YOUR CODE IN THE SYSTEM

```
┌─────────────────────────────────────────────────────────────────┐
│                        MessagingServer                          │
│                    (Your orchestrator)                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
   ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
   │ ServerNode  │  │FailureDetect │  │LeaderElection│
   │(Foundation) │  │   (Core)     │  │  (Advanced)  │
   │             │  │              │  │              │
   │ +record()   │  │ +startHB()   │  │ +triggerRE() │
   │ +isHealthy()│  │ +onHBRec()   │  │              │
   │             │  │ +onHBMiss()  │  │              │
   └─────────────┘  │ +isAlive()   │  └──────────────┘
                    │ +getFailed() │
                    └──────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
    ┌─────────┐      ┌─────────┐        ┌─────────┐
    │ MEMBER 2│      │ MEMBER 3│        │ MEMBER 4│
    │   Rep   │      │ TimeSyn │        │ Consens │
    └─────────┘      └─────────┘        └─────────┘
```

---

## 📡 API CONTRACTS: What Others Call From You

### **MEMBER 2 → YOUR CODE (Replication)**

**When:** Every message replication attempt

```
ReplicationManager.replicateMessage(msg, allReplicas)
    │
    └─→ Call YOUR method: failureDetector.getFailedNodes()
            │
            Returns: [nodeId1, nodeId2, ...]
            │
            Usage: Skip these nodes in replication
            
            REQUIREMENT:
            ├─ Must be thread-safe
            ├─ Must return fresh list (not cached from 5 seconds ago)
            ├─ Response time < 1ms
            └─ Result must be consistent with isNodeAlive()
```

**Example (what Member 2 writes):**
```java
// In ReplicationManager.java
public void replicateMessage(Message msg, List<ServerNode> allReplicas) {
    // Step 1: Get failed nodes
    List<String> failedNodes = failureDetector.getFailedNodes();
    
    // Step 2: Filter them out
    List<ServerNode> healthyReplicas = allReplicas.stream()
        .filter(node -> !failedNodes.contains(node.getNodeId()))
        .collect(Collectors.toList());
    
    // Step 3: Only send to healthy nodes
    for (ServerNode replica : healthyReplicas) {
        messageReplicator.replicate(msg, replica);
    }
}
```

### **MEMBER 3 → YOUR CODE (Time Sync)**

**When:** Periodic clock synchronization

```
ClockSynchronizer.synchronizeClocks(allNodes)
    │
    └─→ Call YOUR method: failureDetector.isNodeAlive(nodeId)
            │
            Returns: true/false
            │
            Usage: Only sync clocks with alive nodes
            Skip dead nodes in sync process
            
            REQUIREMENT:
            ├─ Must be thread-safe
            ├─ Must return immediately (< 1ms)
            ├─ consistent with getFailedNodes()
            └─ No blocking operations
```

**Example (what Member 3 writes):**
```java
// In ClockSynchronizer.java
public void synchronizeClocks(List<ServerNode> nodes) {
    List<ServerNode> aliveNodes = nodes.stream()
        .filter(node -> failureDetector.isNodeAlive(node.getNodeId()))
        .collect(Collectors.toList());
    
    // Only sync with alive nodes
    for (ServerNode node : aliveNodes) {
        // Calculate avg time, send correction
    }
}
```

### **MEMBER 4 → YOUR CODE (Leader Election)**

**When:** Current leader dies

```
FailureDetector.onHeartbeatMissed(leaderId)
    │
    └─→ IF leader died:
        │
        └─→ Call YOUR method: leaderElection.triggerReelection()
                │
                Returns: void
                │
                Usage: Start new election immediately
                
                REQUIREMENT:
                ├─ Be thread-safe (might be called from multiple HB threads)
                ├─ Increment term immediately
                ├─ Clear currentLeader
                └─ Call startElection(healthy candidates)
```

**Example (what you write in FailureDetector):**
```java
public void onHeartbeatMissed(String nodeId) {
    // ... mark as dead ...
    
    // IF the dead node is the leader
    if (leaderElection.getCurrentLeader() != null &&
        leaderElection.getCurrentLeader().getNodeId().equals(nodeId)) {
        
        // IMMEDIATELY trigger re-election
        leaderElection.triggerReelection();
    }
}
```

**Then Member 4 will (in their code):**
```java
// In LeaderElection.java
public void triggerReelection() {
    // 1. Get healthy nodes from MessagingServer
    List<ServerNode> candidates = messagingServer.getAllNodes().stream()
        .filter(node -> failureDetector.isNodeAlive(node.getNodeId()))
        .collect(Collectors.toList());
    
    // 2. Start election among healthy nodes
    startElection(candidates);
}
```

---

## 🔄 YOUR INTERNAL FLOW

### **Heartbeat Processing Loop (Runs Every 2 Seconds)**

```
FailureDetector.startHeartbeat(node)
    │
    └─→ executor.scheduleAtFixedRate(task every 2000ms)
            │
            └─→ For each monitored node:
                    │
                    1. Check: time_since_last_HB > 5000ms?
                    │
                    ├─ NO: Continue monitoring (node healthy)
                    │
                    └─ YES: Call onHeartbeatMissed()
                            │
                            └─→ onHeartbeatMissed(nodeId)
                                │
                                1. Increment failureCount[nodeId]
                                │
                                2. If count >= 3:
                                │
                                ├─ Add to failedNodes set
                                │
                                ├─ IF this was the leader:
                                │  │
                                │  └─ Call leaderElection.triggerReelection()
                                │
                                └─ LOG failure for debugging
```

### **Heartbeat Reception Flow (When MSG Received)**

```
NetworkLayer receives PONG from nodeId
    │
    └─→ failureDetector.onHeartbeatReceived(nodeId)
            │
            1. Update lastHeartbeatTime[nodeId] = NOW
            │
            2. Set failureCount[nodeId] = 0
            │
            3. If nodeId was in failedNodes:
            │
            └─→ Remove from failedNodes (NODE RECOVERED!)
                    │
                    └─ Log: "Node recovered: {}"
```

---

## ⚙️ INTERNAL STATE MANAGEMENT

**What you maintain (Thread-safe collections mandatory):**

```
ConcurrentHashMap<String, Long> lastHeartbeatTime
    Key: nodeId
    Value: timestamp when last heartbeat received
    Usage: detect failures by checking age

ConcurrentHashMap<String, Integer> failureCount
    Key: nodeId
    Value: number of consecutive failures
    Usage: declare dead when count >= 3

ConcurrentHashMap.newKeySet<String> failedNodes
    Elements: nodeIds of dead nodes
    Usage: quick lookup for "is this node dead?"

volatile ServerNode currentLeader
    Value: the elected leader
    Usage: determine if a dead node was the leader
```

---

## 🎯 CRITICAL TIMING REQUIREMENTS

**These affect the entire system - DO NOT CHANGE:**

```
HEARTBEAT_INTERVAL_MS = 2000
├─ How often HB sent to each node
├─ Too slow: Failures detected slowly
├─ Too fast: Wastes bandwidth
└─ 2 seconds: Balanced choice

HEARTBEAT_TIMEOUT_MS = 5000
├─ How long to wait for PONG before considering it a miss
├─ Too short: False positives (network hiccups trigger failures)
├─ Too long: Slow failure detection
├─ 5 seconds: Tolerates one network glitch

MAX_FAILURES_BEFORE_DEAD = 3
├─ How many consecutive misses before declaring dead
├─ 3 misses × 2000ms interval = 6 seconds detection time
├─ Total time to detect failure: ~6-10 seconds
└─ Meets requirement: detect within 10 seconds
```

**Why these exact values?**
- Agreed upon with entire team
- Tested in DESIGN.md
- Member 2 & 3 depend on these timeouts
- Member 4 expects re-election < 10 seconds

---

## 🚨 FAILURE SCENARIOS & EXPECTED BEHAVIOR

### **Scenario 1: Node Network Partition (Temporary)**

```
Timeline:
├─ T=0: Node A <→ Network partitioned from Node B
├─ T=2000: FailureDetector sends HB to B (no PONG)
├─ T=4000: HB to B again (no PONG) - failureCount[B] = 2
├─ T=6000: HB to B again (no PONG) - failureCount[B] = 3
│          └─→ ADD to failedNodes
│          └─→ LOG: "Node B failed"
│          └─→ Notify Member 2: adjust replication
├─ T=6100: Network repaired, PONG received from B
│          └─→ REMOVE from failedNodes
│          └─→ LOG: "Node B recovered"
└─ T=∞: System continues normally
```

**Your code must handle:** Network glitch that lasts < 6 seconds

### **Scenario 2: Leader Dies**

```
Timeline:
├─ T=0: Leader (A) crashes
├─ T=2000: FailureDetector sends HB to A (timeout)
├─ T=4000: HB to A (timeout) - failureCount[A] = 2
├─ T=6000: HB to A (timeout) - failureCount[A] = 3
│          └─→ ADD A to failedNodes
│          └─→ CHECK: Was A the leader?
│          │  └─→ YES!
│          │  └─→ CALL: leaderElection.triggerReelection()
│          └─→ LOG: "Leader failed, re-election triggered"
├─ T=6100: LeaderElection starts election
│          └─→ Candidates: [B, C] (A excluded, D excluded if down)
│          └─→ Winner: B (higher priority)
│          └─→ New leader: B
├─ T=8000: Member 2 detects leader changed
│          └─→ Adjusts replication targets
└─ T=∞: System runs with B as new leader
```

**Your code must handle:**
1. Detect leader is dead
2. Immediately trigger re-election (don't wait!)
3. New leader elected < 5 seconds

### **Scenario 3: Cascading Failures (Multiple Nodes**)**

```
Timeline:
├─ T=0: Nodes B & C crash (network issues)
├─ T=5000: Both marked as failed (failureCount = 3)
├─ T=5010: LeaderElection.triggerReelection() called IF leader was B/C
├─ T=9000: Only A & D alive, new leader elected
└─ T=∞: Quorum = 2, continues with 2 nodes
```

**Your code must handle:** Multiple simultaneous failures

---

## ✅ API COMPATIBILITY MATRIX

**What each member expects from you:**

| Member | Method | Input | Output | Thread-Safe | Latency |
|--------|--------|-------|--------|-------------|---------|
| **2** | `getFailedNodes()` | - | `List<String>` | ✅ Yes | < 1ms |
| **2** | `isNodeAlive(id)` | String nodeId | boolean | ✅ Yes | < 1ms |
| **3** | `isNodeAlive(id)` | String nodeId | boolean | ✅ Yes | < 1ms |
| **4** | `triggerReelection()` | - | void | ✅ Yes | < 100ms |

**Compatibility guarantee:**
```
✅ getFailedNodes() and isNodeAlive() must be consistent
   // If getFailedNodes().contains(nodeId), then isNodeAlive(nodeId) = false
   // If isNodeAlive(nodeId) = true, then !getFailedNodes().contains(nodeId)

✅ All methods must be thread-safe
   // Members 2 & 3 call you from multiple threads

✅ No blocking I/O in your methods
   // These methods are called frequently, must be fast

✅ triggerReelection() must not block for > 100ms
   // Member 4 needs to start election immediately
```

---

## 🔐 THREAD SAFETY GUARANTEES

**You provide:**

```java
// All of these are already thread-safe:
ConcurrentHashMap<String, Long> lastHeartbeatTime
ConcurrentHashMap<String, Integer> failureCount
ConcurrentHashMap.newKeySet<String> failedNodes

// Your methods must be:
public boolean isNodeAlive(String nodeId)
    → Thread-safe? YES, only reads concurrent maps
    
public List<String> getFailedNodes()
    → Thread-safe? YES, returns snapshot from keySet()
    
public void onHeartbeatMissed(String nodeId)
    → Thread-safe? MUST BE, called from executor thread
    
public void onHeartbeatReceived(String nodeId)
    → Thread-safe? MUST BE, called from network thread
```

---

## 📋 DEPENDENCY CHECKLIST

**Before Members 2, 3, 4 can work:**

- [ ] **FailureDetector.java**
  - [ ] `getFailedNodes()` implemented
  - [ ] `isNodeAlive(nodeId)` implemented
  - [ ] Both thread-safe
  - [ ] Both fast (< 1ms)

- [ ] **ServerNode.java**
  - [ ] `isHealthy()` implemented (used by Members 3 & 2)
  - [ ] Returns reliable value
  - [ ] Thread-safe access

- [ ] **MessagingServer.java**
  - [ ] `onNodeFailure(nodeId)` notifies members
  - [ ] FailureDetector started properly

- [ ] **LeaderElection.java**
  - [ ] `triggerReelection()` works correctly
  - [ ] Called when you detect leader death
  - [ ] New leader elected within 5 seconds

---

**You're the foundation. Build it solid! 🏗️**
