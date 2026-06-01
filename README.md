# Dynamo-lite: Distributed Key-Value Store
## Software Requirements Specification + Implementation Plan

**Version:** 2.0 (Final)  
**Date:** May 2026  
**Author:** Developer  
**Infrastructure:** 3 Lab PCs (Linux, always-on) + 1 HP Laptop (Linux)

---

## Part 1 — Software Requirements Specification (SRS)

---

### Section 1 — Introduction

#### 1.1 Purpose

This document is the authoritative specification for Dynamo-lite — a distributed,
fault-tolerant, in-memory key-value store built from scratch in Java, inspired by
Amazon's Dynamo paper (2007). It defines all functional and non-functional
requirements, the wire protocol, threading model, and module boundaries.

Every requirement has an ID, priority, and is verifiable by a concrete test case.

#### 1.2 Document Conventions

- `SHALL` → Mandatory (must implement)
- `SHOULD` → Recommended (implement if time allows)
- `MAY` → Optional / future scope
- `FR-xx` → Functional Requirement
- `NFR-xx` → Non-Functional Requirement
- `DC-xx` → Design Constraint
- `ER-xx` → Error Handling Requirement

#### 1.3 Hardware Topology (Final)

```
Node A  — Lab PC #1    — Ubuntu Linux  — Port 7001  (always-on)
Node B  — Lab PC #2    — Ubuntu Linux  — Port 7001  (always-on)
Node C  — Lab PC #3    — Ubuntu Linux  — Port 7001  (always-on)
Node D  — HP Laptop    — Ubuntu Linux  — Port 7001/7002
Admin   — HP Laptop    — Ubuntu Linux  — CLI dashboard, connects to all nodes
```

All machines are on the same university LAN subnet.

---

### Section 2 — System Overview

#### 2.1 What We Are Building

A from-scratch distributed key-value store. No Spring. No Netty. No Maven.
Raw TCP sockets, hand-written protocol parser, custom consistent hash ring,
quorum-based replication across real LAN-connected machines.

#### 2.2 Core Capabilities

- `PUT(key, value)` / `GET(key)` / `DELETE(key)` over TCP
- Consistent hashing with 150 virtual nodes per physical node
- Quorum replication: default N=3, W=2, R=2 (all configurable)
- Heartbeat-based failure detection (PING/PONG state machine)
- Coordinator pattern — any node routes any request
- Hinted handoff — store for dead nodes, forward on recovery
- Dynamic node JOIN / graceful LEAVE with ring rebalancing
- Read repair — fix stale replicas lazily on GET
- Per-node metrics (ops/sec, latency histogram, replication lag)
- Admin Dashboard CLI — real-time cluster view from one terminal
- Benchmark harness — multi-client concurrent load test
- Fault injection script — iptables-based network partition testing

#### 2.3 What Is Deliberately Out of Scope

| Feature | Why Excluded |
|---|---|
| Disk persistence (WAL, SSTable) | Adds storage complexity; not the learning goal |
| Gossip protocol | Requires weeks alone; not worth rushing |
| Vector clocks | Complex; eventual consistency is sufficient |
| Merkle tree / anti-entropy | Needs vector clocks first |
| TLS / authentication | Trusted university LAN |
| HTTP / REST API | Raw TCP is the learning goal |

These are documented as future scope, not forgotten — this shows architectural maturity.

---

### Section 3 — Functional Requirements

#### FR-01 — PUT Operation

```
ID          : FR-01
Priority    : CRITICAL
Description : The system SHALL store a value for a key. Overwrite on duplicate.
Inputs      : key (String, 1–256 bytes, UTF-8, no spaces, no newlines)
              value (String, 1–1,048,576 bytes)
Outputs     : OK             → stored successfully on W nodes
              ERROR <code>   → failure with reason
Pre         : Node is in RUNNING state
Post        : Value readable via GET from any R nodes
Test        : PUT username alice → OK
              GET username       → VALUE alice
```

#### FR-02 — GET Operation

```
ID          : FR-02
Priority    : CRITICAL
Description : The system SHALL return the value for a key.
              Returns NOT_FOUND if no replica has the key.
              Returns READ_FAILURE if fewer than R replicas respond.
Inputs      : key (String)
Outputs     : VALUE <value>  → found
              NOT_FOUND      → absent on all reachable replicas
              READ_FAILURE   → quorum unreachable
Test        : GET nonexistent → NOT_FOUND
              GET username    → VALUE alice (after PUT)
```

#### FR-03 — DELETE Operation

```
ID          : FR-03
Priority    : HIGH
Description : The system SHALL delete a key from all N replicas (W quorum).
              DELETE on non-existent key → NOT_FOUND.
Outputs     : OK / NOT_FOUND / DELETE_FAILURE
```

#### FR-04 — Input Validation

```
ID          : FR-04
Priority    : CRITICAL

Key rules:
  Length   : 1–256 bytes
  Encoding : UTF-8
  Disallowed: space (protocol delimiter), \n, \0

Value rules:
  Length   : 1–1,048,576 bytes (1 MB)
  Values MAY contain spaces (protocol uses split(" ", 3))

Violations: ERROR INVALID_KEY or ERROR INVALID_VALUE
```

#### FR-05 — Consistent Hash Ring Construction

```
ID          : FR-05
Priority    : CRITICAL
Description : On startup, each node SHALL build an identical hash ring from the
              static cluster config file.
Algorithm   : MD5 of vnode ID string → first 4 bytes → unsigned 32-bit long
Ring size   : [0, 2^32)
Invariant   : Same config → identical ring on all nodes (routing depends on this)
Data struct : TreeMap<Long, NodeInfo> — O(log n) ceilingKey for clockwise lookup
```

#### FR-06 — Virtual Nodes

```
ID          : FR-06
Priority    : CRITICAL
Description : Each physical node SHALL own V=150 virtual positions on the ring.
Purpose     : Uniform load distribution; smooth rebalancing on join/leave
vnode ID    : "<nodeId>-vnode-<i>" for i in [0, 150)
Position    : hash("<nodeId>-vnode-<i>") mod 2^32
```

#### FR-07 — Key-to-Node Mapping

```
ID          : FR-07
Priority    : CRITICAL
Description : To find the node responsible for key K:
              1. pos = hash(K) mod 2^32
              2. ring.ceilingEntry(pos) — first vnode clockwise
              3. If null (past last entry), ring.firstEntry() — wrap around
              4. Return that entry's physical NodeInfo
Complexity  : O(log n) with TreeMap, n = total vnodes (e.g., 600 for 4 nodes)
```

#### FR-08 — Preference List

```
ID          : FR-08
Priority    : CRITICAL
Description : Determine N distinct physical nodes for replication of key K:
              1. Find coordinator (FR-07)
              2. Walk clockwise, collecting physical nodes
              3. Skip vnodes for already-collected physical nodes
              4. Stop when N distinct physical nodes collected
```

#### FR-09 — Quorum Write

```
ID          : FR-09
Priority    : CRITICAL

Protocol:
  1. Coordinator receives PUT(key, value) from client
  2. Builds preference list [N1, N2, N3]
  3. Writes to self if in list
  4. Sends INTERNAL REPLICATE PUT to remaining nodes in PARALLEL
     (ExecutorService — not sequential)
  5. Waits for W acks total within REPLICATION_TIMEOUT (5000ms)
  6. Returns OK to client when W reached; ERROR WRITE_FAILURE otherwise

Default: N=3, W=2 → need 2 of 3 acks (self counts as 1)
```

#### FR-10 — Quorum Read

```
ID          : FR-10
Priority    : CRITICAL

Protocol:
  1. Coordinator receives GET(key)
  2. Queries R nodes from preference list in PARALLEL
  3. Waits for R responses within timeout
  4. Phase 1: returns any value (all replicas should agree if W+R>N)
  5. Phase 2 (Read Repair): asynchronously compare all R values;
     if stale detected, push update to stale replica

Default: N=3, R=2 → need 2 of 3 responses
```

#### FR-11 — Internal Replication Protocol

```
ID          : FR-11
Priority    : CRITICAL
Description : Node-to-node messages use INTERNAL prefix, separate from client protocol.

Commands:
  INTERNAL REPLICATE PUT <key> <value>
  INTERNAL REPLICATE DELETE <key>
  INTERNAL REPLICATE GET <key>
  INTERNAL PING <senderNodeId>
  INTERNAL PONG <senderNodeId>
  INTERNAL FORWARD PUT <key> <value>
  INTERNAL FORWARD GET <key>
  INTERNAL HINT PUT <targetNodeId> <key> <value>
  INTERNAL HINT FLUSH <targetNodeId>
  INTERNAL JOIN <nodeId> <host> <port>
  INTERNAL LEAVE <nodeId>
  INTERNAL STATUS

Not exposed to clients.
```

#### FR-12 — Heartbeat Protocol

```
ID          : FR-12
Priority    : CRITICAL

Mechanism:
  Every HEARTBEAT_INTERVAL ms, send INTERNAL PING to all known peers.
  If PONG received within HEARTBEAT_TIMEOUT: mark ALIVE, reset miss counter.
  If no reply: increment miss counter.
  If miss counter >= HEARTBEAT_MISS_THRESHOLD: mark DOWN.

Config defaults:
  HEARTBEAT_INTERVAL       = 1000 ms
  HEARTBEAT_TIMEOUT        = 2000 ms
  HEARTBEAT_MISS_THRESHOLD = 3

Detection time: ~3–5 seconds after crash
```

#### FR-13 — Cluster State

```
ID          : FR-13
Priority    : CRITICAL
Description : Each node maintains a local view:
  ConcurrentHashMap<nodeId, NodeStatus>   (ALIVE | SUSPECTED | DOWN)
  ConcurrentHashMap<nodeId, AtomicInteger> missCounters
  ConcurrentHashMap<nodeId, Long>          lastSeenTimestamp

State machine:
  ALIVE → SUSPECTED : first missed heartbeat
  SUSPECTED → DOWN  : miss_count >= threshold
  ANY → ALIVE       : PONG received (reset miss_count = 0)

Note: Views are local; nodes may briefly disagree — this is acceptable
(eventual consistency of cluster membership view).
```

#### FR-14 — Failure-Aware Routing

```
ID          : FR-14
Priority    : CRITICAL
Description : When building preference list, skip DOWN nodes.
              Use next ALIVE node on ring as substitute.
              Substitute node stores data with a HINT:
              "this data belongs to <targetNodeId>"
```

#### FR-15 — Hinted Handoff

```
ID          : FR-15
Priority    : HIGH

Structure:
  HintedHandoffStore: ConcurrentHashMap<targetNodeId, List<HintedEntry>>
  HintedEntry: { key, value, operation, timestamp }

Capacity    : 1000 hinted entries per target node (Phase 1)

Forwarding:
  On HeartbeatManager receiving PONG from previously DOWN node:
  1. Push all hinted entries to recovered node
  2. Clear hinted store for that node
  3. Log result
```

#### FR-16 — Coordinator Pattern

```
ID          : FR-16
Priority    : CRITICAL
Description : Any node may accept any client request.
              That node becomes the coordinator:
              - If self is in preference list: handle locally + replicate
              - If self is NOT in preference list: FORWARD to correct coordinator
```

#### FR-17 — Dynamic Node JOIN

```
ID          : FR-17
Priority    : HIGH

Protocol:
  1. New node starts, reads cluster.config
  2. Sends INTERNAL JOIN <nodeId> <host> <port> to seed node
  3. Seed node broadcasts JOIN to all peers
  4. All nodes add new node to ring and cluster state
  5. Coordinator identifies keys now owned by new node
  6. Migrates affected keys: INTERNAL REPLICATE PUT for each key

Post: New node is ALIVE in all cluster state views;
      ring is consistent across all nodes.
```

#### FR-18 — Graceful Node LEAVE

```
ID          : FR-18
Priority    : HIGH

Protocol:
  1. Node receives LEAVE command (CLI or signal)
  2. Broadcasts INTERNAL LEAVE <nodeId> to all peers
  3. Identifies keys it owns → pushes each to successor on ring
  4. Clears own storage
  5. Shuts down TCP server and exits cleanly
```

#### FR-19 — Read Repair

```
ID          : FR-19
Priority    : HIGH
Description : After returning value to client on GET:
              Compare all R responses received.
              If any replica returned a value that is absent or differs
              from the majority, push the correct value to that replica
              asynchronously (do not block the client response).
```

#### FR-20 — Per-Node Metrics

```
ID          : FR-20
Priority    : HIGH

MetricsCollector per node:
  AtomicLong: putCount, getCount, deleteCount, replicationSuccessCount
  AtomicLong: replicationFailureCount, hintedHandoffCount, readRepairCount
  LongAdder[] latencyBuckets: [<1ms, <5ms, <10ms, <50ms, <100ms, >100ms]

Exposed via: STATUS command → JSON blob
Updated by : every PUT, GET, DELETE path (before and after timing)
```

#### FR-21 — Admin Dashboard CLI

```
ID          : FR-21
Priority    : HIGH

DynamoAdmin.java:
  Connects to all known nodes, polls STATUS every 2 seconds.
  Renders terminal table:

  ┌─────────────┬────────┬───────────┬───────────┬───────────┐
  │  Node       │ Status │ Ops/sec   │ p99 (ms)  │ Keys      │
  ├─────────────┼────────┼───────────┼───────────┼───────────┤
  │  NodeA      │ ALIVE  │  1,240    │    8ms    │  12,430   │
  │  NodeB      │ ALIVE  │  1,198    │    9ms    │  12,380   │
  │  NodeC      │ DOWN   │     —     │     —     │     —     │
  │  NodeD      │ ALIVE  │    430    │    7ms    │   4,110   │
  └─────────────┴────────┴───────────┴───────────┴───────────┘
  Ring: 4 nodes × 150 vnodes = 600 positions  N=3 W=2 R=2

  ANSI color: GREEN = ALIVE, RED = DOWN, YELLOW = SUSPECTED.
  Clears screen and redraws every 2 seconds.
```

#### FR-22 — CLI Client

```
ID          : FR-22
Priority    : HIGH

DynamoCLI.java commands:
  connect <host> <port>    → TCP connection to a node
  put <key> <value>        → store key-value
  get <key>                → retrieve value
  delete <key>             → remove key
  status                   → ask node for cluster status
  disconnect               → close connection
  quit / exit              → exit client

Example session:
  dynamo> connect 192.168.1.101 7001
  Connected to NodeA (192.168.1.101:7001)
  dynamo> put username alice
  OK
  dynamo> get username
  VALUE alice
  dynamo> get nonexistent
  NOT_FOUND
```

#### FR-23 — Static Node Configuration

```
ID          : FR-23
Priority    : CRITICAL

File: cluster.config (one per node; peers list identical on all)

Format:
  node.id=NodeA
  node.host=192.168.1.101
  node.port=7001
  peers=NodeB:192.168.1.102:7001,NodeC:192.168.1.103:7001,NodeD:192.168.1.104:7001
  replication.n=3
  replication.w=2
  replication.r=2
  vnodes.count=150
  heartbeat.interval.ms=1000
  heartbeat.timeout.ms=2000
  heartbeat.miss.threshold=3
```

---

### Section 4 — Wire Protocol Specification

#### 4.1 Philosophy

Custom text protocol, line-delimited, space-separated. Same family as Redis RESP
but simpler. Every byte is hand-written and debuggable with `nc` / `telnet`.

#### 4.2 Client-to-Node Protocol (External)

```
Request format: <COMMAND> [arg1] [arg2]\n
Response format: <STATUS> [payload]\n

Commands:
  PUT <key> <value>\n      (split on first 2 spaces: line.split(" ", 3))
  GET <key>\n
  DELETE <key>\n
  STATUS\n
  PING\n

Responses:
  OK                       PUT / DELETE success
  VALUE <value>            GET success
  NOT_FOUND                Key absent
  PONG                     Heartbeat response
  STATUS <json>            Cluster metrics JSON

Error codes:
  ERROR INVALID_KEY
  ERROR INVALID_VALUE
  ERROR KEY_TOO_LARGE
  ERROR VALUE_TOO_LARGE
  ERROR WRITE_FAILURE
  ERROR READ_FAILURE
  ERROR DELETE_FAILURE
  ERROR NODE_DOWN
  ERROR UNKNOWN_COMMAND
```

Multi-word values: `PUT greeting hello world` → `line.split(" ", 3)` gives
`["PUT", "greeting", "hello world"]`. The value preserves internal spaces.

#### 4.3 Node-to-Node Protocol (Internal)

All node-to-node messages are prefixed with `INTERNAL` on the same port.
A single port per node — one firewall rule, simpler configuration.

```
INTERNAL REPLICATE PUT <key> <value>
INTERNAL REPLICATE DELETE <key>
INTERNAL REPLICATE GET <key>
INTERNAL PING <senderNodeId>
INTERNAL PONG <senderNodeId>
INTERNAL FORWARD PUT <key> <value>
INTERNAL FORWARD GET <key>
INTERNAL HINT PUT <targetNodeId> <key> <value>
INTERNAL HINT FLUSH <targetNodeId>
INTERNAL JOIN <nodeId> <host> <port>
INTERNAL LEAVE <nodeId>
INTERNAL STATUS
```

#### 4.4 Connection Model

Client connections are persistent (multi-command per TCP session).
Node-to-node connections are short-lived (open → send → receive → close).
`setSoTimeout()` on every socket: replication timeout = 5000ms,
heartbeat timeout = 2000ms, client idle timeout = 30000ms.

---

### Section 5 — Non-Functional Requirements

#### 5.1 Performance

| ID | Requirement | Target |
|---|---|---|
| NFR-01 | GET latency (single node, no failure) | < 5ms p99 |
| NFR-02 | PUT latency (quorum write, LAN) | < 50ms p99 |
| NFR-03 | Concurrent connections per node | ≥ 50 |
| NFR-04 | Heartbeat detection time | < 5 seconds |
| NFR-05 | Node startup time | < 3 seconds |
| NFR-06 | Throughput under benchmark | ≥ 1000 ops/sec per node |

#### 5.2 Reliability

| ID | Requirement |
|---|---|
| NFR-07 | System stays available (R/W) with 1 of 3 preference-list nodes down |
| NFR-08 | No data loss if W=2 nodes alive during write |
| NFR-09 | Every failure returns an explicit error code — never silent |
| NFR-10 | No request blocks indefinitely — all paths have timeouts |

#### 5.3 Consistency

| ID | Requirement |
|---|---|
| NFR-11 | Eventual consistency model (AP in CAP theorem) |
| NFR-12 | With R + W > N (2+2=4>3), GET reflects latest quorum write |
| NFR-13 | Read repair converges stale replicas within 2 subsequent reads |

#### 5.4 Concurrency

| ID | Requirement |
|---|---|
| NFR-14 | All shared state uses thread-safe structures (ConcurrentHashMap) |
| NFR-15 | Fixed thread pool (50 client handlers, 3 replication workers) |
| NFR-16 | Replication fanout is parallel (ExecutorService, Future<>) |
| NFR-17 | HashRing is immutable after construction — no sync required |
| NFR-18 | Metrics use AtomicLong / LongAdder — no locks |

#### 5.5 Maintainability

| ID | Requirement |
|---|---|
| NFR-19 | Single responsibility per class |
| NFR-20 | All config externalized to cluster.config |
| NFR-21 | Log format: timestamp | nodeId | threadName | level | message |
| NFR-22 | No magic numbers — all constants in DynamoConfig |
| NFR-23 | StorageEngine is an interface — swap disk implementation in Phase 2 |

---

### Section 6 — Design Constraints

| ID | Constraint |
|---|---|
| DC-01 | Pure Java 17 language features (runs on JDK 17, 21, 26) |
| DC-02 | No external libraries in Phase 1. Java standard library only. |
| DC-03 | Raw TCP sockets. No HTTP, no gRPC, no Netty. |
| DC-04 | No build framework. `javac` + shell scripts. |
| DC-05 | In-memory storage only (ConcurrentHashMap). No disk. |
| DC-06 | No TLS/SSL. Trusted university LAN. |
| DC-07 | Maximum 4 nodes (3 lab PCs + 1 laptop). |
| DC-08 | Always-on infrastructure — all 4 machines persistent. |

---

### Section 7 — Error Handling Requirements

| ID | Rule |
|---|---|
| ER-01 | All exceptions caught at server level; never send stack traces to clients |
| ER-02 | Client errors use exact format: `ERROR <CODE>\n` |
| ER-03 | Replication timeout: 5000ms. Heartbeat timeout: 2000ms. Client idle: 30000ms. |
| ER-04 | One replica failure does NOT fail the client if W-1 others succeeded |
| ER-05 | All errors logged at WARN or ERROR level with full context |
| ER-06 | Port bind failure on startup → clear error message + non-zero exit code |

---

### Section 8 — Package Structure

```
com.dynamo.lite/
├── DynamoNode.java                  ← main() — bootstraps all subsystems
│
├── config/
│   └── DynamoConfig.java            ← loads cluster.config, exposes constants
│
├── server/
│   ├── TcpServer.java               ← ServerSocket accept loop (dedicated thread)
│   ├── ClientHandler.java           ← per-connection thread: read→route→respond
│   └── NodeStatus.java              ← enum: ALIVE, SUSPECTED, DOWN
│
├── protocol/
│   ├── CommandType.java             ← enum: PUT, GET, DELETE, PING, STATUS, INTERNAL_*
│   ├── Request.java                 ← immutable value object
│   ├── RequestParser.java           ← parses raw TCP line → Request
│   └── Response.java                ← builds protocol response strings
│
├── storage/
│   ├── StorageEngine.java           ← interface: get/put/delete/size/keys
│   └── InMemoryStorageEngine.java   ← ConcurrentHashMap implementation
│
├── hashing/
│   ├── HashFunction.java            ← MD5 → unsigned 32-bit long
│   ├── VirtualNode.java             ← (vnodeId, position, physicalNode)
│   └── ConsistentHashRing.java      ← TreeMap<Long, NodeInfo>, ring ops
│
├── cluster/
│   ├── NodeInfo.java                ← immutable: nodeId, host, port
│   ├── ClusterState.java            ← ConcurrentHashMap state + counters
│   └── HeartbeatManager.java        ← ScheduledExecutorService PING/PONG
│
├── replication/
│   ├── ReplicaClient.java           ← opens short-lived TCP to peer nodes
│   ├── ReplicationManager.java      ← quorum reads/writes, parallel fanout
│   └── HintedHandoffStore.java      ← store hints; flush on node recovery
│
├── routing/
│   └── RequestRouter.java           ← decide: handle locally OR forward
│
├── metrics/
│   └── MetricsCollector.java        ← AtomicLong counters + latency histogram
│
└── client/
    ├── DynamoCLI.java               ← interactive CLI client
    └── DynamoAdmin.java             ← real-time multi-node dashboard
```

---

### Section 9 — Key Design Decisions

| # | Decision | Rationale |
|---|---|---|
| D01 | MD5 for ring hashing | Deterministic across JVMs, uniform, fast, no deps. Java hashCode() is NOT uniform. |
| D02 | 150 virtual nodes per physical node | Balances uniformity vs. memory. 600 TreeMap entries is negligible. |
| D03 | TreeMap for ring | O(log n) ceilingKey for clockwise successor. Sorted. No external deps. |
| D04 | Same port, INTERNAL prefix | One firewall rule per node. Simpler on university network. |
| D05 | Short-lived connections for replication | Simpler than pooling. 1–2ms handshake is acceptable on LAN. |
| D06 | Fixed thread pool (50 handlers, 3 replication) | Prevents OOM. Bounded resource under load. |
| D07 | StorageEngine as interface | Phase 2: swap in DiskStorageEngine without changing callers. Dependency inversion. |
| D08 | Immutable ConsistentHashRing (Phase 1) | Zero-cost thread safety. Built once, read concurrently by all threads. |
| D09 | Eventual consistency (AP in CAP) | Availability over consistency. Matches Dynamo's design philosophy. |
| D10 | ScheduledExecutorService for heartbeat | Drift-free periodic execution. Thread.sleep() accumulates drift. |
| D11 | AtomicLong / LongAdder for metrics | Lock-free counter updates on the hot path. No synchronization overhead. |

---

### Section 10 — Requirements Traceability Matrix

| Req ID | Component | Test Reference |
|---|---|---|
| FR-01 | StorageEngine, ReplicationManager | TC-PUT-001 to TC-PUT-010 |
| FR-02 | StorageEngine, ReplicationManager | TC-GET-001 to TC-GET-008 |
| FR-03 | StorageEngine, ReplicationManager | TC-DEL-001 to TC-DEL-005 |
| FR-04 | RequestParser | TC-VAL-001 to TC-VAL-010 |
| FR-05/06 | ConsistentHashRing | TC-HASH-001 to TC-HASH-010 |
| FR-07/08 | ConsistentHashRing | TC-ROUTE-001 to TC-ROUTE-008 |
| FR-09 | ReplicationManager | TC-REP-001 to TC-REP-010 |
| FR-10 | ReplicationManager | TC-QREAD-001 to TC-QREAD-008 |
| FR-12/13 | HeartbeatManager, ClusterState | TC-HB-001 to TC-HB-008 |
| FR-14/15 | RequestRouter, HintedHandoffStore | TC-HINT-001 to TC-HINT-006 |
| FR-16 | RequestRouter | TC-COORD-001 to TC-COORD-008 |
| FR-17/18 | ReplicationManager, ClusterState | TC-JOIN-001, TC-LEAVE-001 |
| FR-19 | ReplicationManager | TC-RR-001 to TC-RR-004 |
| FR-20 | MetricsCollector | TC-METRICS-001 to TC-METRICS-005 |
| FR-21 | DynamoAdmin | TC-ADMIN-001 to TC-ADMIN-003 |
| NFR-14 to NFR-18 | All shared state | TC-CONC-001 to TC-CONC-010 |

---

## Part 2 — Week-by-Week Implementation Plan

---

### How to Use This Plan

Each step builds on the previous one. Test on localhost first, then deploy to lab.
Never skip to the next step with a broken current step.
Code is always written at home; deployment to lab is a separate activity.

---

### Week 1 — Core Infrastructure (Home, Localhost)

**Goal:** A single-node TCP server that accepts PUT/GET/DELETE from `nc` or the CLI.
No distribution yet — just the foundation everything else sits on.

---

#### Step 1 — Project skeleton + configuration (Day 1)

**What to build:**
- Directory structure (see Section 8 above)
- `cluster.config` file (one template)
- `DynamoConfig.java` — parses config, exposes typed getters
- `NodeInfo.java` — immutable value object (nodeId, host, port)
- `DynamoLogger.java` — thin wrapper around System.out with format:
  `[2026-05-15 14:23:01] [NodeA] [Thread-3] [INFO] message here`

**Why first?** Everything depends on config. Solid foundation prevents re-work.

**Test:** Run `DynamoConfig` with a config file. Print all values. Confirm they load correctly.

---

#### Step 2 — Storage engine (Day 1–2)

**What to build:**
- `StorageEngine.java` — interface with `get`, `put`, `delete`, `size`, `keys`
- `InMemoryStorageEngine.java` — `ConcurrentHashMap<String, String>` implementation

**Why this early?** StorageEngine is a dependency of almost everything else. Build it isolated, test it isolated.

**Test (unit, no networking):**
```java
StorageEngine s = new InMemoryStorageEngine();
s.put("name", "alice");
assert s.get("name").get().equals("alice");
s.delete("name");
assert s.get("name").isEmpty();
System.out.println("StorageEngine: all tests passed");
```

---

#### Step 3 — Request parser + protocol objects (Day 2)

**What to build:**
- `CommandType.java` — enum: PUT, GET, DELETE, PING, STATUS, INTERNAL_REPLICATE_PUT, etc.
- `Request.java` — immutable: `{CommandType type, String key, String value, boolean isInternal}`
- `RequestParser.java` — `parse(String line) → Request`
- `Response.java` — static factory: `ok()`, `value(v)`, `notFound()`, `error(code)`, `pong()`

**Critical implementation note:**
```java
// Multi-word value support
String[] parts = line.trim().split(" ", 3);
// parts[0] = command, parts[1] = key, parts[2] = value (may contain spaces)
```

**Test:** Parse every command type including edge cases (empty value, spaces in value, INTERNAL prefix).

---

#### Step 4 — TCP server + client handler (Day 3–4)

**What to build:**
- `TcpServer.java` — `ServerSocket` on configured port; `accept()` loop in dedicated thread; dispatches to thread pool
- `ClientHandler.java` — `Runnable`; wraps socket; `BufferedReader`/`PrintWriter` loop; parses commands; dispatches to storage engine directly (no routing yet); returns responses; loops until `readLine()` returns null (disconnect)

**Thread pool:**
```java
ExecutorService pool = Executors.newFixedThreadPool(config.getClientThreadPoolSize());
// On accept: pool.submit(new ClientHandler(socket, ...));
```

**Critical: setSoTimeout on every accepted socket:**
```java
socket.setSoTimeout(config.getClientIdleTimeoutMs()); // 30000ms
```

**Test with netcat:**
```bash
nc localhost 7001
PUT username alice
# Should receive: OK
GET username
# Should receive: VALUE alice
DELETE username
# Should receive: OK
GET username
# Should receive: NOT_FOUND
```

**Milestone:** Single node, fully functional on localhost. No distribution yet.

---

#### Step 5 — Hash ring + hash function (Day 4–5)

**What to build:**
- `HashFunction.java` — MD5 of input string → unsigned 32-bit long (first 4 bytes of digest)
  ```java
  byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(UTF_8));
  return ((d[0] & 0xFFL) << 24) | ((d[1] & 0xFFL) << 16)
       | ((d[2] & 0xFFL) << 8)  |  (d[3] & 0xFFL);
  ```
- `VirtualNode.java` — `{String vnodeId, long position, NodeInfo physicalNode}`
- `ConsistentHashRing.java` — builds `TreeMap<Long, NodeInfo>` at construction; implements `getCoordinator(key)` and `getPreferenceList(key, n)`

**Test:**
```java
List<NodeInfo> nodes = List.of(nodeA, nodeB, nodeC);
ConsistentHashRing ring = new ConsistentHashRing(nodes, 150, 3);
ring.printRing(); // visual inspection
// For 10,000 test keys, count how many go to each node
// Should be roughly 33% each ± 5%
```

---

#### Step 6 — Cluster state + heartbeat manager (Day 5–7)

**What to build:**
- `ClusterState.java` — `ConcurrentHashMap<String, NodeStatus>` + miss counters + last-seen timestamps. Methods: `markAlive(id)`, `markMissed(id)`, `isAlive(id)`, `getClusterSummary()`
- `HeartbeatManager.java` — `ScheduledExecutorService.scheduleAtFixedRate(...)` every 1000ms; sends `INTERNAL PING <selfId>` to all peers via `ReplicaClient`; processes `PONG` responses; calls `clusterState.markAlive/markMissed`

**State machine:**
```
ALIVE → SUSPECTED (1 miss) → DOWN (3 misses) → ALIVE (any PONG)
```

**Test:** Start two JVMs on localhost (ports 7001 and 7002). Kill one. Verify the other marks it DOWN within 5 seconds. Restart it. Verify it returns to ALIVE.

**Week 1 deliverable:** Single-node storage working. Hash ring built and tested. Heartbeat detecting fake failures on localhost.

---

### Week 2 — Distributed Core (Home + Lab Session 1)

**Goal:** Full 3-node quorum replication, coordinator routing, hinted handoff.
By end of week: `PUT` on NodeA is readable from NodeB and NodeC.

---

#### Step 7 — Replica client (Day 1–2)

**What to build:**
- `ReplicaClient.java` — opens a new TCP connection to a peer node, sends one command, reads response, closes socket. Uses try-with-resources. `setSoTimeout` on every socket.

```java
public Optional<String> send(NodeInfo target, String command) {
    try (Socket s = new Socket(target.host, target.port)) {
        s.setSoTimeout(config.getReplicationTimeoutMs());
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(s.getInputStream()));
        out.println(command);
        return Optional.ofNullable(in.readLine());
    } catch (IOException e) {
        logger.warn("ReplicaClient failed to " + target.nodeId + ": " + e.getMessage());
        return Optional.empty();
    }
}
```

**Test:** Start two JVMs. Have one send `INTERNAL PING NodeA` to the other. Verify `INTERNAL PONG NodeA` comes back.

---

#### Step 8 — Replication manager (Day 2–4)

**What to build:**
- `ReplicationManager.java` — implements `handlePut`, `handleGet`, `handleDelete`

**handlePut algorithm:**
1. `storage.put(key, value)` — local write (ack #1)
2. Get preference list from ring; filter for ALIVE nodes != self
3. For each DOWN node in preference list: `hintStore.add(downNodeId, entry)`
4. Submit parallel replication tasks to `replicationPool` (size = N-1 = 2)
5. Collect acks with `future.get(timeout, MILLISECONDS)`
6. If W acks total (self + collected): return `Response.ok()`
7. Else: return `Response.error("WRITE_FAILURE")`

**Parallel replication with Future:**
```java
List<Future<Boolean>> futures = new ArrayList<>();
for (NodeInfo replica : aliveReplicas) {
    futures.add(replicationPool.submit(() ->
        replicaClient.send(replica, "INTERNAL REPLICATE PUT " + key + " " + value)
            .map("ACK"::equals).orElse(false)));
}
int acks = 1; // self
for (Future<Boolean> f : futures) {
    try {
        if (f.get(timeout, MILLISECONDS)) acks++;
        if (acks >= config.getWriteQuorum()) break;
    } catch (TimeoutException | ExecutionException e) {
        /* count as failed ack */
    }
}
```

**Test (3 JVMs on localhost, ports 7001/7002/7003):**
```bash
# In terminal 1: nc localhost 7001
PUT testkey hello
# → OK
# Check terminal 2 logs: should show INTERNAL REPLICATE PUT received
# In terminal 2: nc localhost 7002
GET testkey
# → VALUE hello
```

---

#### Step 9 — Request router + coordinator pattern (Day 4–5)

**What to build:**
- `RequestRouter.java` — receives parsed `Request`; checks if `self` is in preference list; if yes: delegates to `ReplicationManager`; if no: `ReplicaClient.send(coordinator, forward command)`; returns response to `ClientHandler`

**ClientHandler now delegates to RequestRouter, not directly to storage.**

**Test:** Connect to NodeA. PUT a key whose coordinator is NodeB. Verify NodeA forwards transparently and returns the correct response.

---

#### Step 10 — Hinted handoff store + recovery trigger (Day 5–7)

**What to build:**
- `HintedHandoffStore.java` — `ConcurrentHashMap<String, CopyOnWriteArrayList<HintedEntry>>`; methods: `add(targetNodeId, entry)`, `flush(targetNodeId, replicaClient)`, `size(targetNodeId)`

**Recovery trigger in HeartbeatManager:**
```java
// When transitioning from DOWN → ALIVE:
hintStore.flush(recoveredNodeId, replicaClient);
```

**Test:**
1. Start 3 nodes. Kill Node C (`kill <pid>`).
2. PUT 5 keys whose preference list includes NodeC.
3. Verify OK returned (W=2 achieved with NodeA + NodeB).
4. Verify NodeA or NodeB has hinted entries for NodeC.
5. Restart NodeC. Wait 5 seconds.
6. Verify hinted entries were forwarded. Check NodeC's storage.

**Lab Session 1 goal:**
Deploy steps 1–10 on 3 real lab PCs. Verify basic PUT/GET/replication works across real LAN. Document lab IPs in config files.

---

### Week 3 — Production Features (Lab Sessions 2–3)

**Goal:** Dynamic join/leave, read repair, metrics, admin dashboard.

---

#### Step 11 — Dynamic node JOIN (Day 1–3)

**Protocol:**
1. New node sends `INTERNAL JOIN NodeD <host> <port>` to seed node (NodeA)
2. NodeA validates, adds NodeD to its `ClusterState` and rebuilds ring
3. NodeA broadcasts `INTERNAL JOIN NodeD <host> <port>` to NodeB and NodeC
4. All nodes rebuild ring
5. NodeA identifies keys now owned by NodeD (keys whose first ring successor changed to NodeD)
6. Migrates those keys: `INTERNAL REPLICATE PUT` to NodeD

**Implementation detail for key migration:**
```java
// After ring rebuild, find keys to migrate:
for (String key : storage.keys()) {
    NodeInfo newOwner = newRing.getCoordinator(key);
    NodeInfo oldOwner = oldRing.getCoordinator(key);
    if (newOwner.equals(joiningNode) && !oldOwner.equals(joiningNode)) {
        // This key moved to the new node
        replicaClient.send(joiningNode, "INTERNAL REPLICATE PUT " + key + " " + storage.get(key).get());
    }
}
```

**Test:** Start 3 nodes. Load 1000 keys. Start 4th node. Verify keys migrate. Verify all 4 nodes agree on ring.

---

#### Step 12 — Graceful node LEAVE (Day 3–4)

**What to build:**
Add `LEAVE` command to `DynamoNode.java` shutdown hook and CLI.

**Protocol:**
1. On LEAVE: broadcast `INTERNAL LEAVE NodeX` to all peers
2. For each key owned: push to ring successor via `INTERNAL REPLICATE PUT`
3. Clear own storage
4. Shut down TCP server cleanly

**Test:** Node joins. Load keys. Node leaves gracefully. Verify keys are on successor. Verify ring is consistent.

---

#### Step 13 — Read repair (Day 4–5)

**What to build:**
Extend `ReplicationManager.handleGet()`:

After collecting R responses and returning to client, compare all responses.
If any replica returned a different (or absent) value from the majority:
push the majority value to that replica asynchronously.

```java
// After returning response to client:
Map<String, Long> valueCounts = responses.stream()
    .collect(Collectors.groupingBy(r -> r.value, Collectors.counting()));
String correctValue = valueCounts.entrySet().stream()
    .max(Map.Entry.comparingByValue()).get().getKey();

for (ReplicaResponse r : responses) {
    if (!r.value.equals(correctValue)) {
        replicationPool.submit(() ->
            replicaClient.send(r.node, "INTERNAL REPLICATE PUT " + key + " " + correctValue));
        metrics.incrementReadRepairCount();
    }
}
```

**Test:** Manually corrupt one replica (direct storage write via INTERNAL command). Issue GET. Verify correct value returned. Verify stale replica is fixed within next GET.

---

#### Step 14 — Per-node metrics (Day 5–6)

**What to build:**
- `MetricsCollector.java`

```java
public class MetricsCollector {
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong getCount = new AtomicLong();
    private final AtomicLong deleteCount = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong readFailures = new AtomicLong();
    private final AtomicLong hintedHandoffCount = new AtomicLong();
    private final AtomicLong readRepairCount = new AtomicLong();
    // Latency histogram: [<1ms, <5ms, <10ms, <50ms, <100ms, >100ms]
    private final LongAdder[] latencyBuckets = new LongAdder[6];

    public void recordLatency(long nanos) {
        long ms = nanos / 1_000_000;
        if      (ms < 1)   latencyBuckets[0].increment();
        else if (ms < 5)   latencyBuckets[1].increment();
        else if (ms < 10)  latencyBuckets[2].increment();
        else if (ms < 50)  latencyBuckets[3].increment();
        else if (ms < 100) latencyBuckets[4].increment();
        else               latencyBuckets[5].increment();
    }

    public String toJson() { /* serialize all fields to JSON string */ }
}
```

Inject into `ReplicationManager` and call at start/end of every operation.
Expose via `STATUS` command response.

---

#### Step 15 — Admin dashboard CLI (Day 6–7)

**What to build:**
- `DynamoAdmin.java` — standalone main class

```java
// Every 2 seconds:
System.out.print("\033[H\033[2J"); // ANSI clear screen
for (NodeInfo node : allNodes) {
    String status = replicaClient.send(node, "STATUS").orElse("UNREACHABLE");
    // parse JSON, render table row with ANSI colors:
    // GREEN = ALIVE, RED = DOWN/UNREACHABLE, YELLOW = SUSPECTED
    System.out.printf(ROW_FORMAT, node.nodeId, parseStatus(status), ...);
}
Thread.sleep(2000);
```

**Lab Session 2 goal:** Kill one lab PC node using `sudo systemctl stop dynamo-nodeA` or just `kill`. Watch Admin Dashboard show it go from ALIVE → SUSPECTED → DOWN in real time. Restore it. Watch ALIVE again. Show hinted handoff forwarding in logs.

**Lab Session 3 goal:** Full 4-node fault injection with iptables network partition:
```bash
# On NodeA — block all traffic to NodeB (simulate network partition):
sudo iptables -A INPUT -s <NodeB-IP> -j DROP
sudo iptables -A OUTPUT -d <NodeB-IP> -j DROP

# Run PUT/GET through other nodes — verify system still works
# Heal the partition:
sudo iptables -F
```

Document observed behaviour: split views, quorum protection, recovery.

---

### Week 4 — Testing, Polish, Demo (Home + Lab Session 4)

**Goal:** Production-quality benchmarks, a demo script, documentation, and a project README with real data.

---

#### Step 16 — Benchmark harness (Day 1–3)

**What to build:**
- `BenchmarkClient.java` — multi-threaded load generator

```java
// Configurable: numThreads, numOperations, readWriteRatio, keySpaceSize
// Outputs per-second throughput, latency percentiles (p50, p95, p99)
// Example output:
// Threads: 20  Ops: 100,000  Duration: 23.4s
// Throughput: 4,273 ops/sec
// Latency — p50: 3ms  p95: 12ms  p99: 28ms
// Errors: 0
```

Run multiple benchmark configurations:
- Single node (baseline)
- 3-node quorum
- 3-node with 1 node failing mid-run

Record numbers. These go in your README and CV.

---

#### Step 17 — Fault injection script (Day 3–4)

**What to build:**
- `fault-inject.sh` — bash script using `iptables` and `kill`

```bash
#!/bin/bash
# Fault injection scenarios:
# 1. Kill a node process
# 2. Network partition (iptables DROP)
# 3. Slow network (tc netem add delay 200ms)
# 4. Heal all faults

case "$1" in
  kill-nodeA)
    ssh lab-pc-1 "pkill -f DynamoNode"
    ;;
  partition-A-B)
    ssh lab-pc-1 "sudo iptables -A OUTPUT -d $NODE_B_IP -j DROP"
    ssh lab-pc-2 "sudo iptables -A OUTPUT -d $NODE_A_IP -j DROP"
    ;;
  slow-link)
    ssh lab-pc-1 "sudo tc qdisc add dev eth0 root netem delay 200ms"
    ;;
  heal)
    for host in lab-pc-1 lab-pc-2 lab-pc-3; do
      ssh $host "sudo iptables -F; sudo tc qdisc del dev eth0 root 2>/dev/null; true"
    done
    ;;
esac
```

---

#### Step 18 — Demo script (Day 4–5)

**What to build:**
A scripted, reproducible demo sequence:

```
Demo Script — Dynamo-lite Live Demo

1. START: Show 4-node cluster in Admin Dashboard — all green
2. LOAD: Insert 1000 keys via BenchmarkClient
3. VERIFY: GET random keys from different nodes — all respond correctly
4. KILL: Kill Lab PC #2 (NodeB)
5. OBSERVE: Admin Dashboard shows SUSPECTED → DOWN within 5s
6. WRITE: PUT new keys (system still accepts writes — quorum of 2)
7. READ: GET those keys — correct values returned from NodeA and NodeC
8. REVIVE: Restart NodeB
9. OBSERVE: Dashboard shows ALIVE; hinted handoff log shows keys forwarded
10. VERIFY: GET from NodeB — correct values (hinted handoff worked)
11. PARTITION: iptables partition between NodeA and NodeC
12. OBSERVE: Quorum still works (NodeA+NodeB, NodeB+NodeC are both quorums)
13. HEAL: Remove iptables rules
14. BENCHMARK: Run BenchmarkClient — show throughput + latency numbers
```

Rehearse this twice. It should run in under 15 minutes.

---

#### Step 19 — Documentation (Day 5–7)

**README.md must include:**
- What it is (1 paragraph)
- What it implements (Dynamo paper concepts)
- Architecture diagram (ASCII or image)
- How to run (cluster.config + start scripts)
- Benchmark results (actual numbers from your lab run)
- What you learned
- Future scope (disk persistence, gossip, vector clocks — with 2-sentence explanation of each)

**This is the difference between a student project and a portfolio piece.**

---

### Test Scenarios Reference

| Test | Pass Condition |
|---|---|
| TC-PUT-001 | PUT key value → OK |
| TC-PUT-002 | PUT same key twice → second GET returns second value |
| TC-PUT-003 | PUT with empty key → ERROR INVALID_KEY |
| TC-PUT-004 | PUT with 1MB value → OK |
| TC-GET-001 | GET nonexistent → NOT_FOUND |
| TC-GET-002 | GET after PUT → correct value |
| TC-HASH-001 | 10,000 keys: each node receives ~33% ± 5% |
| TC-HASH-002 | Same config on 3 nodes → identical ring (same TreeMap contents) |
| TC-REP-001 | PUT via NodeA → GET from NodeB → correct value |
| TC-REP-002 | Kill NodeC → PUT still returns OK (W=2: NodeA+NodeB) |
| TC-HB-001 | Kill node → marked DOWN within 5 seconds |
| TC-HB-002 | Revive node → marked ALIVE within 2 heartbeat cycles |
| TC-HINT-001 | Kill NodeC → PUT 5 keys → revive NodeC → NodeC has all 5 keys |
| TC-COORD-001 | Connect to any node, PUT/GET any key → correct result |
| TC-JOIN-001 | 4th node joins → ring consistent across all 4 nodes |
| TC-LEAVE-001 | Node leaves gracefully → successor has its keys |
| TC-RR-001 | Manually stale replica → GET correct value → GET stale replica → now correct |
| TC-CONC-001 | 50 concurrent clients PUT/GET simultaneously → no data corruption |

---

### Resume Line (Fill in After Benchmarks)

> Built a production-grade distributed key-value store from scratch in Java 17 — no
> frameworks, raw TCP sockets. 4-node cluster on real LAN hardware, implementing
> consistent hashing (MD5, 150 virtual nodes/physical node), quorum replication
> (N=3, W=2, R=2), heartbeat failure detection (<5s), hinted handoff, read repair,
> and dynamic node join/leave. Benchmarked at **\_\_\_\_ ops/sec** with **\_\_\_ms p99**
> latency. Demonstrated CAP theorem behaviour (AP partition tolerance) via iptables
> fault injection across 4 always-on Linux machines.

---

### Key Commands Cheat Sheet

```bash
# Compile all
find src -name "*.java" | xargs javac -d out/

# Start a node
java -cp out com.dynamo.lite.DynamoNode config/nodeA.config

# Start Admin Dashboard
java -cp out com.dynamo.lite.client.DynamoAdmin config/nodeA.config

# Test with netcat
nc 192.168.1.101 7001
PUT greeting hello
GET greeting
DELETE greeting
STATUS

# Kill and revive for testing
pkill -f "DynamoNode.*nodeC"
java -cp out com.dynamo.lite.DynamoNode config/nodeC.config &

# Network partition (run as root/sudo)
sudo iptables -A INPUT  -s <NodeB-IP> -j DROP
sudo iptables -A OUTPUT -d <NodeB-IP> -j DROP
sudo iptables -F  # heal all rules

# Slow network simulation
sudo tc qdisc add dev eth0 root netem delay 200ms
sudo tc qdisc del dev eth0 root  # remove
```

---

*End of Document*
