# API Documentation

**Project:** Distributed Systems Project  
**Version:** 1.0.0  
**Last Updated:** March 20, 2026

---

## Overview

This document describes all public APIs and interfaces used in the distributed system.

---

## Core Interfaces

### 1. IDistributedNode
**Purpose:** Represents a node in the distributed system

```java
public interface IDistributedNode {
    
    /**
     * Initialize the node with given configuration
     * @param config Configuration for this node
     * @throws InitializationException if initialization fails
     */
    void initialize(NodeConfiguration config) throws InitializationException;
    
    /**
     * Send a message to another node
     * @param targetNode Target node ID
     * @param message Message to send
     * @return Message ID
     * @throws SendException if send operation fails
     */
    String sendMessage(String targetNode, Message message) throws SendException;
    
    /**
     * Receive messages
     * @return Next message from queue
     */
    Message receiveMessage();
    
    /**
     * Get current state of the node
     * @return Current state
     */
    NodeState getState();
    
    /**
     * Shutdown the node gracefully
     */
    void shutdown();
}
```

### 2. IMessage
**Purpose:** Represents a message in the system

```java
public interface IMessage {
    
    /**
     * Get unique message identifier
     * @return Message ID
     */
    String getMessageId();
    
    /**
     * Get message type
     * @return Message type
     */
    MessageType getType();
    
    /**
     * Get message timestamp
     * @return Creation timestamp
     */
    long getTimestamp();
    
    /**
     * Get message payload
     * @return Message data
     */
    Object getPayload();
}
```

### 3. ISynchronizer
**Purpose:** Provides synchronization primitives

```java
public interface ISynchronizer {
    
    /**
     * Acquire a lock
     * @param lockName Name of the lock
     * @param timeout Timeout in milliseconds
     * @return true if lock acquired, false if timeout
     */
    boolean acquireLock(String lockName, long timeout);
    
    /**
     * Release a lock
     * @param lockName Name of the lock
     */
    void releaseLock(String lockName);
    
    /**
     * Wait at a barrier
     * @param barrierName Name of the barrier
     * @param expectedCount Expected number of threads
     */
    void waitAtBarrier(String barrierName, int expectedCount);
}
```

---

## Core Classes

### Message
Standard message implementation

**Constructor:**
```java
Message(String id, MessageType type, Object payload)
```

**Methods:**
```java
public String getMessageId()          // Returns message ID
public MessageType getType()          // Returns message type
public long getTimestamp()            // Returns creation time
public Object getPayload()            // Returns payload
public void setRetryCount(int count)  // Set retry attempts
```

### NodeConfiguration
Configuration container for nodes

**Properties:**
```java
String nodeId              // Unique node identifier
int port                   // Port number for communication
int threadPoolSize         // Thread pool size
long timeoutMs            // Operation timeout
Map<String, String> params // Custom parameters
```

---

## Enums

### MessageType
```java
public enum MessageType {
    REQUEST,      // Request message
    RESPONSE,     // Response message
    HEARTBEAT,    // Heartbeat
    ACK,          // Acknowledgement
    ERROR         // Error message
}
```

### NodeState
```java
public enum NodeState {
    INITIALIZING,     // Initialization phase
    READY,            // Ready to accept messages
    PROCESSING,       // Processing a message
    FAILED,           // Node failed
    SHUTDOWN          // Node shutdown
}
```

---

## Exceptions

### Custom Exceptions Hierarchy
```
DistributedSystemException (extends Exception)
├── InitializationException
├── SendException
├── ReceiveException
├── TimeoutException
├── SynchronizationException
└── StateException
```

---

## Communication Protocol

### Request-Response Pattern
```
Client                          Server
  │                               │
  │──── REQUEST(id, payload) ───→ │
  │                               │
  │                        Process │
  │                               │
  │ ←──── RESPONSE(id, result) ── │
  │                               │
```

### Message Retry Policy
- Maximum retries: 3
- Retry interval: 1000ms
- Backoff: Exponential (1s, 2s, 4s)

---

## Error Handling

### Standard Error Codes
```
1000: Unknown error
1001: Message format error
1002: Node not found
1003: Timeout
1004: Synchronization error
```

### Timeout Values
```
Operation Timeout: 5000ms (default, configurable)
Connection Timeout: 3000ms
Read Timeout: 5000ms
Write Timeout: 5000ms
```

---

## Code Examples

### Basic Usage Pattern
```java
// Initialize system
DistributedSystem system = new DistributedSystem();
system.initialize(config);

// Create node
IDistributedNode node = system.createNode("node1");

// Send message
Message msg = new Message("msg1", MessageType.REQUEST, payload);
String msgId = node.sendMessage("node2", msg);

// Receive response
Message response = node.receiveMessage(msgId, 5000);

// Shutdown
system.shutdown();
```

### Concurrent Access
```java
ISynchronizer sync = system.getSynchronizer();

// Acquire lock
if (sync.acquireLock("critical_section", 5000)) {
    try {
        // Critical section code
    } finally {
        sync.releaseLock("critical_section");
    }
}
```

---

## Performance Considerations

- **Message Queue Size:** Configurable (default 1000)
- **Thread Pool Size:** Configurable (default 10)
- **Memory Usage:** Depends on message queue size and payload
- **Latency:** Depends on network and processing time

---

## Versioning

Current API Version: **1.0.0**

### Version History
| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | Mar 20, 2026 | Initial API |

---

## TO BE FILLED

- [ ] Specific operation contracts
- [ ] Authentication/Authorization details
- [ ] Rate limiting information
- [ ] Service level agreements
