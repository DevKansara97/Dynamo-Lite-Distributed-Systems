# Dynamo-lite

A distributed key-value store built from scratch in Java, inspired by Amazon's Dynamo paper. No frameworks, no build tools — raw TCP sockets, custom consistent hashing, and quorum replication across a real multi-node cluster.

---

## The idea

Most databases run on a single machine. If that machine crashes, your data is unavailable. If your data grows too large, you're stuck.

Dynamo solves this by spreading data across multiple nodes and keeping multiple copies of each key. This project implements those same ideas from scratch — not as a library call, but as working Java code you can run on real machines.

---

## What it does

```
PUT username alice   →  OK
GET username         →  VALUE alice
DELETE username      →  OK
GET username         →  NOT_FOUND
```

Under the hood, a single `PUT` fans out to 3 nodes in parallel. The system returns `OK` as soon as 2 of 3 acknowledge. If one node is down, the write still succeeds — and the missing copy is delivered automatically when that node comes back.

---

## Core concepts implemented

### Consistent Hashing
Nodes and keys are mapped onto a ring from 0 to 2³². To find which node owns a key, hash it and walk clockwise to the first node. When a node joins or leaves, only ~1/N keys move — not the entire dataset.

**The problem with naive assignment:** with 1 virtual node per physical node, one node might get 54% of keys while another gets 13%. With 150 virtual nodes per physical node, each gets ~33%.

| Virtual nodes | NodeA | NodeB | NodeC |
|---|---|---|---|
| 1 | 54.25% | 13.75% | 32.00% |
| 10 | 32.16% | 36.53% | 31.31% |
| 150 | ~33.3% | ~33.3% | ~33.3% |

### Quorum Replication (N=3, W=2, R=2)
Every key is stored on 3 nodes. A write succeeds when 2 acknowledge. A read queries 2 nodes. Since W+R=4 > N=3, you always read what you last wrote — even in an eventually consistent system.

### Failure Detection
Every node PINGs its peers every second. Three missed responses → node marked `DOWN`. Detection happens in under 5 seconds. State: `ALIVE → SUSPECTED → DOWN → ALIVE`.

### Hinted Handoff
When a node is down during a write, the coordinator stores the data locally with a note: *"this belongs to NodeC."* When NodeC recovers, everything stored for it is forwarded automatically. No data loss, no manual intervention.

---

## Architecture

```
Client (CLI / GUI)
        │
        ▼  TCP
 ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 │    NodeA     │────▶│    NodeB     │────▶│    NodeC     │
 │    :7001     │◀────│    :7002     │◀────│    :7003     │
 └──────────────┘     └──────────────┘     └──────────────┘
        │                    │                    │
        └────────────────────┴────────────────────┘
                   Consistent Hash Ring
```

Every node is identical. Any node accepts any request, acts as coordinator, replicates to peers, and responds to the client.

See [`diagrams/`](diagrams/) for detailed sequence diagrams, state machines, and the threading model.

---

## Project structure

```
src/com/dynamo/lite/
├── DynamoNode.java              ← main() entry point
├── config/                      ← loads cluster.config
├── server/                      ← TCP accept loop + per-connection handler
├── protocol/                    ← request parser, response builder
├── storage/                     ← StorageEngine interface + ConcurrentHashMap impl
├── hashing/                     ← MD5 hash function, virtual nodes, ring
├── cluster/                     ← NodeInfo, ClusterState, HeartbeatManager
├── replication/                 ← quorum writes/reads, hinted handoff
├── routing/                     ← RequestRouter (local vs. replicate)
└── client/
    ├── DynamoCLI.java           ← interactive terminal client
    └── DynamoGUI.java           ← Swing desktop dashboard
```

---

## Running it

**Prerequisites:** Java 17+. Nothing else.

**1. Compile**
```bash
./compile.sh
```

**2. Start the cluster**
```bash
./start-cluster.sh
```
Starts NodeA (:7001), NodeB (:7002), NodeC (:7003) as background processes and opens the GUI dashboard. Logs go to `logs/`.

**3. Try it**
```bash
# Write to NodeA
nc localhost 7001
PUT city ahmedabad

# Read from NodeC — a completely different node
nc localhost 7003
GET city
# → VALUE ahmedabad
```

**4. Test fault tolerance**
```bash
# Kill NodeB
kill $(cat logs/nodeB.pid)

# System still works — W=2 satisfied by NodeA + NodeC
nc localhost 7001
PUT order-99 confirmed
# → OK

# Restart NodeB
java -cp out com.dynamo.lite.DynamoNode config/nodeB.config &

# NodeB auto-receives the missed write via hinted handoff
nc localhost 7002
GET order-99
# → VALUE confirmed
```

**5. Stop everything**
```bash
./stop-cluster.sh
```

---

## GUI Dashboard

```bash
./gui.sh
```

A Swing desktop app that polls all nodes every 2 seconds — shows live status (ALIVE / UNREACHABLE), key counts, and lets you run PUT/GET/DELETE from a UI without touching the terminal.

---

## Configuration

```properties
# config/nodeA.config
node.id=NodeA
node.host=localhost
node.port=7001
peers=NodeB:localhost:7002,NodeC:localhost:7003
replication.n=3
replication.w=2
replication.r=2
vnodes.count=150
heartbeat.interval.ms=1000
heartbeat.miss.threshold=3
```

To deploy on real machines, change `localhost` to actual IPs.

---

## Design decisions worth knowing

**Why MD5 for hashing?**
Java's built-in `hashCode()` is not uniform and not stable across JVM restarts. MD5 gives a deterministic, uniform 32-bit output — essential for all nodes building an identical ring from the same config.

**Why `TreeMap` for the ring?**
`TreeMap.ceilingKey()` gives O(log n) clockwise lookup. The ring never changes after startup (Phase 1), so there's no synchronization cost on reads.

**Why short-lived TCP connections for replication?**
Simpler than connection pooling. A TCP handshake on LAN takes 1–2ms — acceptable when replication timeout is 5000ms.

**Why `ScheduledExecutorService` for heartbeat?**
`Thread.sleep()` accumulates drift. A scheduled task fires at precise intervals regardless of how long the previous heartbeat took.

---

## What I learned

- Consistent hashing sounds simple until you implement it and see the load imbalance with few virtual nodes. The 150-vnode number in the real Dynamo paper is not arbitrary.
- Quorum reads and writes are a tunable dial. W=1, R=3 gives fast writes and slow reads. W=3, R=1 is the opposite. W=2, R=2 is the balance point.
- The hardest part of distributed systems is not the happy path — it's deciding what to do when a network call times out. Every line of the replication manager is a decision about that.

---

## Future scope

- **Disk persistence** — `StorageEngine` is an interface; a WAL-backed implementation would add durability across restarts
- **Gossip protocol** — replace static config with dynamic peer discovery
- **Vector clocks** — detect and resolve concurrent conflicting writes
- **Read repair** — fix stale replicas lazily on GET without any manual step

---

## References

- [Amazon Dynamo Paper — DeCandia et al., SOSP 2007](https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)
- [Consistent Hashing — Karger et al., 1997](https://www.cs.princeton.edu/courses/archive/fall09/cos518/papers/chash.pdf)