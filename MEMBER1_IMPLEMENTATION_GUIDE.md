# 🔴 MEMBER 1: COLLABORATIVE IMPLEMENTATION GUIDE

**Fault Tolerance & Failover — Ready to Build Collaboratively**

---

## 📊 YOUR TASK AT A GLANCE

You control **what other members depend on**:

```
MEMBER 1 (You)
    ├─→ MEMBER 2 needs: getFailedNodes(), isNodeAlive()
    ├─→ MEMBER 3 needs: isNodeAlive()
    └─→ MEMBER 4 needs: triggerReelection() calls when leader dies
```

**If you build this wrong → Everyone else breaks.**
**If you build this right → Everything integrates smoothly.**

---

## 🎯 YOUR 4 FILES - IMPLEMENTATION SEQUENCE

### **SEQUENCE (Build in this order for dependencies):**

1. **ServerNode.java** (foundation) — Health tracking
2. **FailureDetector.java** (core logic) — Heartbeat monitoring
3. **MessagingServer.java** (integration) — Failure handlers
4. **LeaderElection.java** (advanced) — Re-election trigger

---

## 📋 FILE 1: ServerNode.java (Foundation Layer)

**What it does:** Tracks individual node health

**Methods YOU need to add:**

```java
// Health Tracking - Required by FailureDetector
public void recordHeartbeat()
    → Update lastHeartbeat timestamp

public long getTimeSinceLastHeartbeat()
    → Return: System.currentTimeMillis() - lastHeartbeat

public boolean isHealthy()
    → Return: getTimeSinceLastHeartbeat() < 6000ms
```

**Compatibility Notes:**
- ✅ **Thread-safe:** Use `volatile` for `lastHeartbeat`
- ✅ **Concurrent access:** Members 2 & 3 will call `isHealthy()` from different threads
- ✅ **No sleeping:** Keep these methods fast (< 1ms)

**Integration Points:**
```
ServerNode.isHealthy()
    ↓ called by ↓
FailureDetector.onHeartbeatMissed()  [Member 1]
MessageOrderer.periodicSync()         [Member 3]
ReplicationManager.selectHealthyReplicas() [Member 2]
```

**Implementation Skeleton:**
```java
private volatile long lastHeartbeat;

public void recordHeartbeat() {
    // TODO: 1 line - update lastHeartbeat to current time
    lastHeartbeat = System.currentTimeMillis();
}

public long getTimeSinceLastHeartbeat() {
    // TODO: 1 line - calculate time since last heartbeat
    return System.currentTimeMillis() - lastHeartbeat;
}

public boolean isHealthy() {
    // TODO: 1 line - alive if last HB < 6 seconds ago
    return getTimeSinceLastHeartbeat() < 6000;
}
```

---

## 📋 FILE 2: FailureDetector.java (Core Logic)

**What it does:** Sends heartbeats, detects failures

**Critical Methods (in order of importance):**

```java
1. startHeartbeat(ServerNode node)
   → Start periodic heartbeat to this node
   → Called once when node joins cluster
   
2. onHeartbeatReceived(String nodeId)
   → Called when heartbeat ACKed
   → Reset failure counter, remove from failedNodes
   
3. onHeartbeatMissed(String nodeId)
   → Called after 3 failures (6 seconds)
   → Add to failedNodes, trigger re-election if leader

4. isNodeAlive(String nodeId)
   → Return true if recently heard from (< 6 seconds)
   → **CRITICAL:** Called by Members 2 & 3 to skip dead nodes

5. getFailedNodes()
   → Return Set<String> of dead nodes
   → **CRITICAL:** Called by Member 2 for replication
```

**Constraints (MUST follow):**

1. **Thread Safety:**
   ```java
   private Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
   private Map<String, Integer> failureCount = new ConcurrentHashMap<>();
   private Set<String> failedNodes = ConcurrentHashMap.newKeySet();
   
   // These are already declared - DO NOT change their types
   ```

2. **Timeouts (DO NOT CHANGE - agreed with teams):**
   ```java
   HEARTBEAT_INTERVAL_MS = 2000    // Send every 2 sec
   HEARTBEAT_TIMEOUT_MS = 5000     // Consider dead after 5 sec
   MAX_FAILURES_BEFORE_DEAD = 3    // 3 misses = declare dead
   ```
   
   **Why these values?**
   - 2000ms: Low overhead
   - 5000ms: Tolerates one network hiccup
   - 3 failures → 6 seconds total = quick failure detection

3. **Executor:**
   ```java
   executor = Executors.newScheduledThreadPool(1);
   // Use this for periodic heartbeat tasks
   ```

**Implementation Skeleton:**

```java
public void startHeartbeat(ServerNode node) {
    String nodeId = node.getNodeId();
    lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
    failureCount.put(nodeId, 0);
    
    // TODO: Use executor.scheduleAtFixedRate(task, initialDelay, period, unit)
    // Task should:
    //   1. Check if time_since_hb > HEARTBEAT_TIMEOUT_MS
    //   2. If yes: call onHeartbeatMissed()
    //   3. Otherwise: continue monitoring
}

public void onHeartbeatReceived(String nodeId) {
    lastHeartbeatTime.put(nodeId, System.currentTimeMillis());
    failureCount.put(nodeId, 0);
    
    if (failedNodes.contains(nodeId)) {
        failedNodes.remove(nodeId);
        logger.info("Node recovered: {}", nodeId);
    }
}

public void onHeartbeatMissed(String nodeId) {
    // TODO: 1. Increment failureCount[nodeId]
    //       2. If count >= MAX_FAILURES_BEFORE_DEAD:
    //          a. Add to failedNodes
    //          b. If leader died: call leaderElection.triggerReelection()
    //          c. Notify Member 2: "member2ReplicationManager.onNodeFailed()"
}

public boolean isNodeAlive(String nodeId) {
    if (!lastHeartbeatTime.containsKey(nodeId)) return false;
    
    long timeSince = System.currentTimeMillis() - lastHeartbeatTime.get(nodeId);
    return timeSince < HEARTBEAT_TIMEOUT_MS && !failedNodes.contains(nodeId);
}

public List<String> getFailedNodes() {
    return new ArrayList<>(failedNodes);
}
```

**Integration Points:**

```
Who calls you:
├─ MessagingServer.start() → startHeartbeat(node)
├─ Network layer → onHeartbeatReceived(nodeId)
├─ Periodic monitor → onHeartbeatMissed(nodeId)
└─ LeaderElection.triggerReelection()

Who you call:
├─ leaderElection.triggerReelection()
    if (leaderElection.getCurrentLeader().getNodeId().equals(nodeId)) {
        leaderElection.triggerReelection();
    }
└─ [TODO: Add callback to notify Member 2]
```

---

## 📋 FILE 3: MessagingServer.java (Integration)

**What it does:** Orchestrates node management and failure handling

**Methods YOU need to add:**

```java
public void onNodeFailure(String nodeId)
    → Called by FailureDetector when node dies
    → Notify other modules about failure

public void removeFailedNode(String nodeId)
    → Actually remove node from cluster

public void notifyReplication(String failedNodeId)
    → Tell Member 2: "this node is dead, adjust replication targets"

public void notifyConsensus(String failedNodeId)
    → Tell Member 4: "this node is dead, might affect quorum"
```

**Implementation Skeleton:**

```java
public void onNodeFailure(String nodeId) {
    // TODO: 1. Log the failure
    //       2. Call removeFailedNode()
    //       3. Notify other modules
    logger.error("Node failed: {}", nodeId);
    removeFailedNode(nodeId);
    
    if (failureDetector != null) {
        // Member 2 watches getFailedNodes()
        // No explicit call needed - Member 2 will check it
    }
}

public void removeFailedNode(String nodeId) {
    nodes.remove(nodeId);
    failureDetector.stopHeartbeat(nodeId);  // Clean up FD state
    
    // TODO: Update any data structures that track node membership
}
```

**Who calls this:**
- `FailureDetector.onHeartbeatMissed()` → `MessagingServer.onNodeFailure()`

---

## 📋 FILE 4: LeaderElection.java (Advanced)

**What it does:** Elect a leader, handle re-election when leader dies

**Critical Method:**

```java
public void triggerReelection()
    → Called by FailureDetector when leader is detected dead
    → Start new election immediately
```

**Implementation Skeleton:**

```java
public void triggerReelection() {
    electionLock.lock();
    try {
        currentLeader = null;  // Clear old leader
        currentTerm++;         // Start new term
        
        // TODO: Call startElection() with healthy nodes from MessagingServer
        // List<ServerNode> candidates = messagingServer.getHealthyNodes()
        // startElection(candidates);
    } finally {
        electionLock.unlock();
    }
}
```

---

## 🔗 INTEGRATION REQUIREMENTS

**To work with other members, you MUST provide these APIs:**

### **For Member 2 (Replication):**
```java
// Member 2 calls this to get failed nodes
failureDetector.getFailedNodes()
    → Returns: List<String> of dead nodeIds
    → Usage: Skip these nodes when replicating

failureDetector.isNodeAlive(String nodeId)
    → Returns: boolean - true if node recently heartbeated
    → Usage: Double-check if node is safe to send to
```

**Compatibility requirement:** 
- Response time: < 1ms
- Thread-safe: Can call from multiple threads
- Atomic: Member 2 will call multiple times per message - must be consistent

### **For Member 3 (Time Sync):**
```java
failureDetector.isNodeAlive(String nodeId)
    → Usage: Skip clock sync with dead nodes
    → Member 3 calls this before syncing clocks
```

### **For Member 4 (Consensus):**
```java
leaderElection.triggerReelection()
    → Member 4 will call this when leader dies
    → You MUST start new election immediately
    → Must be thread-safe
```

---

## 🧪 TESTING CHECKLIST (For your QA)

Before you consider this DONE, verify:

```
✅ Test 1: Heartbeat sent and received
  - Start node
  - Verify onHeartbeatReceived() called
  - Verify isNodeAlive() returns true

✅ Test 2: Failure detection
  - Start node
  - Stop heartbeat (simulate network failure)
  - Wait 6+ seconds
  - Verify node added to getFailedNodes()

✅ Test 3: Leader failure re-election
  - Kill leader node
  - Verify onHeartbeatMissed() detected it
  - Verify leaderElection.triggerReelection() called
  - Verify new leader elected within 5 seconds

✅ Test 4: Node recovery
  - Kill node
  - Verify in failedNodes
  - Restart node
  - Verify getFailedNodes() removes it

✅ Test 5: Thread-safe concurrent access
  - Multiple threads call isNodeAlive() simultaneously
  - Verify no crashes, no data corruption
  - Verify results are consistent

✅ Test 6: No memory leaks
  - Run for 10 minutes with node failures/recoveries
  - Check memory usage stays constant
  - Verify executor threads cleanup properly
```

---

## 📞 COLLABORATION CHECKLIST

**Before you start coding:**

- [ ] Understand your 4 files and their sequence
- [ ] Review integration points above
- [ ] Confirm timeouts with team (2000ms, 5000ms, 3 failures)
- [ ] Check Member 2's ReplicationManager skeleton
  - See how it will call `getFailedNodes()`
  - Ensure your API matches
- [ ] Check Member 3's MessageOrderer, Member 4's RaftConsensus
  - Understand how they'll use your code
  - They depend on you being correct!

**During coding:**

- [ ] Code in sequence: ServerNode → FailureDetector → MessagingServer → LeaderElection
- [ ] After each file: Run `mvn clean compile` to check for syntax errors
- [ ] Test each method as you complete it
- [ ] Push to your feature branch daily: `git commit -m "Day X: [what you implemented]"`

**Integration (Day 8):**

- [ ] All methods implemented and tested
- [ ] Member 2 can call your APIs without errors
- [ ] Member 3 can call your APIs without errors
- [ ] Member 4's re-election works with your code

---

## 🚀 READY TO START?

**Tomorrow (Day 1):**
1. Read this guide fully
2. Understand the 4-file sequence
3. Read your 4 skeleton files
4. Meet with team to confirm timeouts and APIs

**Day 2:**
1. Start with ServerNode.java (add 3 methods)
2. Test each method
3. Ready for:Day 3: FailureDetector.java

**Days 3-5:**
- Implement FailureDetector, MessagingServer, LeaderElection
- Test each day
- Commit each day

---

**You've got this! Build it right, and everyone else can build on top! 💪**
