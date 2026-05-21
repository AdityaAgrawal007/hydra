# Hydra - Distributed Key-Value Store

## What Problem Does This Solve?

- **Scalability** - Centralized databases are limited by the capacity of a single machine (vertical scaling)
- **Performance Bottleneck** - causes high latency
- **Modern cloud native apps** span over multiple locations, a single db server can cause enormous latency
- **High Availability** - central server fails entire system goes down

These are inherent problems of traditional centralized systems.

## Who is the Client?

A backend dev at any company.

---

## Functional Requirements

1. Client can store a value against a key (PUT)
2. Client can retrieve a value by key (GET)
3. Client can delete a key (DELETE)
4. Client can check if a key exists without fetching the value (EXISTS)
5. Client can configure replication factor, meaning how many nodes store a copy of each key
6. Client receives an error or fallback if a key is not found
7. Client can connect to any node in the cluster and still perform operations (no single entry point)
8. System should re-route requests if the target node is down
9. Client can configure the system via a YAML configuration file (replication factor, node addresses, port, timeout thresholds)

---

## Non Functional Requirements

- **Availability** - system continues serving reads and writes even if a minority of nodes go down (here we choose to trade off between data not being lost over nodes being down cause of the type of use cases of a DDBMS, i.e. stuff like leaderboards, shopping carts etc where data lost is not tolerated but down time can be)
- **Eventual Consistency** - after a write, all nodes will converge to the same value, but not necessarily instantly
- **Low Latency** - reads and writes should complete in single digit milliseconds under normal conditions
- **Horizontal Scalability** - achieved via adding more nodes should increase storage capacity and throughput linearly
- **Fault Tolerance** - system should tolerate up to N/2-1 node failures where N is total nodes
- **Durability** - data written to the system must persist across node restarts and crashes

---

## High Level Design

- **Storage Engine** - performs the actual operations (put, get, del) on data
- **Replication Manager** - handles the actual copying of data to all replica nodes in parallel, decides which nodes are replicas for a given key (sync logic), decides which other nodes to copy data on
- **Coordinator** - coordinator receives the client request, uses the consistent hashing ring to find the target nodes, routes the request to them, waits for acknowledgment, and responds to the client. It's the orchestrator of the entire operation.
- **Consistent Hashing Ring** - handles node addition and removal, sort of a registry that maps all keys to the nodes used by both read/writes. The input is the key then a hash is generated which maps to a particular node and that way the output is the node that you want to send data to, it circles it like if the value is increasing it just circles the hash
- **Gossip Protocol / Failure Detector** - nodes pass info to each other about which nodes have failed thus maintaining a record of failed nodes

---

## Data Flow

### PUT Operation

1. Client writes data, it is received by the coordinator
2. The coordinator uses consistent hashing ring to lookup which node shall the key be sent to and then sends it to that node
3. It is received by the replication manager that writes to all the replica nodes in parallel
4. The above write operation is performed by the storage engine
5. When the majority of the nodes have sent their ack of successful write to the coordinator which further passes its ack to the client

### GET Operation

1. Client requests a value for a key, which is received by the coordinator
2. The coordinator uses the consistent hashing ring to identify which nodes hold replicas for that key
3. The coordinator fetches the data from any one of the replica nodes
4. The fetch operation is performed by the storage engine
5. The storage engine returns data to the replica node, the replica node returns it to the coordinator, the coordinator returns it to the client

### DELETE Operation

1. Client sends key to delete, received by the coordinator
2. Coordinator uses consistent hashing ring to find on which node is the key stored and forwards the request to delete the key to the replication manager
3. The replication manager deletes the key from the replica nodes
4. The delete operation is performed by the storage engine
5. When it is successfully performed majority of the replica nodes send ack of successful delete to the coordinator which further passes its ack to the client

---

## Failure Modes

1. **Storage engine failure** - a node fails mid-write, data is partially written or lost
2. **Node failure** - a node goes down completely, its keys become temporarily unreachable
3. **Coordinator failure** - coordinator dies mid-operation after sending writes to some replicas but before collecting quorum, leaving replicas in an inconsistent state
4. **Network partition** - cluster splits into two groups that cannot communicate, both keep serving requests independently and diverge
5. **Stale node rejoin** - a node comes back online after downtime with outdated data, serving stale reads until it catches up
6. **Write conflict** - two clients write to the same key simultaneously on different replica nodes, creating conflicting versions
7. **Replication lag** - a write is acknowledged after quorum but the remaining replicas haven't synced yet, a read hitting those replicas returns stale data

---

## Design Decisions

- **For countering Failure Mode 6:** we use Last Write Wins like Cassandra DB for the sake of simplicity unlike using Vector Clocks like Amazon's DynamoDB

---

## Low Level Design

### 1. Storage Engine

Performs the actual operations (put, get, del) on data.

**Input:**
- The key to perform the operation on
- The operation itself

**Output:**
- Ack if operation was completed successfully, along with the value (for get operation only)

#### Parts

**Write Ahead Log (WAL)** - operation is first appended to WAL on disk, then written to memtable in memory. If node crashes, WAL is replayed on restart to reconstruct the memtable
- Input: write operation (key, value, operation type)
- Output: confirmation that operation is durably logged to disk

**Memtable** - this is where data is first stored, it exists on memory, used for caching
- Input: write operation from WAL, or key lookup from GET
- Output: on write, confirmation data is in memory. On read, returns value if key exists in memtable, else signals to check SSTable

**SSTable** - resides on the disk, the data from the memtable in memory gets copied here
- Input: flushed memtable data on write, or key lookup on read
- Output: on write, immutable sorted file on disk. On read, returns value if key exists, else signals key not found

**Compaction** - Over time you accumulate multiple SSTables on disk. A background process called compaction merges them periodically to remove duplicates and keep reads fast
- Input: multiple SSTable files on disk
- Output: single merged SSTable with duplicates removed and deleted keys purged

---

### 2. Consistent Hashing Ring

The ring is a sorted map (`TreeMap<Integer, String>`) where keys are hash positions and values are node identifiers. Determines which nodes own which keys for both reads and writes. Handles node addition and removal without reshuffling all keys.

#### Functions

**Add Node** - places a new node on the ring at hash(node) position, reassigns keys from the next clockwise node to this new node
- Input: node identifier (IP address)
- Output: node placed on ring, affected keys reassigned

**Remove Node** - removes a node from the ring, its keys are reassigned to the next clockwise node
- Input: node identifier
- Output: node removed, its keys reassigned to next node

**Lookup Key** - hashes the key, walks clockwise to find the nearest node
- Input: key
- Output: node identifier responsible for that key

**Get Replicas** - walks clockwise from the key's position and picks N distinct nodes
- Input: key, replication factor N
- Output: list of N node identifiers that should hold replicas of this key

---

### 3. Gossip Protocol / Failure Detector

Each node periodically pings a random subset of other nodes and shares what it knows about the cluster state. Information about node failures spreads through the cluster like a rumor, eventually reaching all nodes without a central coordinator.

**Internal data structure:** `ConcurrentHashMap<String, NodeState>` where key is node identifier and value is its state (ALIVE, SUSPECTED, DEAD) plus a heartbeat counter and timestamp.

#### Functions

**Send Heartbeat** - node increments its own heartbeat counter and sends it to K random nodes
- Input: none
- Output: heartbeat message (node ID, heartbeat count, timestamp) sent to K random peers

**Receive Heartbeat** - node receives heartbeat from a peer, updates its local map if the received heartbeat count is higher than what it has
- Input: heartbeat message (node ID, heartbeat count, timestamp)
- Output: updated local cluster state map

**Detect Failure** - runs on a timer, checks all nodes in local map, if a node's heartbeat hasn't been updated within a timeout threshold it is marked SUSPECTED, if it remains unupdated past a second threshold it is marked DEAD
- Input: current timestamp
- Output: updated node states, triggers removal of DEAD nodes from consistent hashing ring

**Merge State** - when two nodes exchange state, they merge by taking the higher heartbeat count for each node entry
- Input: remote node's cluster state map
- Output: merged local cluster state map

---

### 4. Coordinator

Receives client requests, uses the consistent hashing ring to identify target nodes, routes operations to them, waits for quorum acknowledgment, and responds to the client. Every client request passes through a coordinator, and any node in the cluster can act as one.

**Internal data structure:** none persistent. Stateless per request.

#### Functions

**Handle Put** - orchestrates a write operation end to end
- Input: key, value
- Output: ack to client after quorum of replicas confirm write

**Handle Get** - orchestrates a read operation
- Input: key
- Output: value returned from one replica node, or key not found error

**Handle Delete** - orchestrates a delete operation
- Input: key
- Output: ack to client after quorum of replicas confirm delete

**Handle Exists** - checks if a key exists without fetching value
- Input: key
- Output: boolean

**Route Request** - uses consistent hashing ring to find target nodes for a given key
- Input: key, replication factor N
- Output: list of N node identifiers

**Collect Quorum** - sends operation to target nodes in parallel, waits until majority respond
- Input: list of target nodes, operation, key, value
- Output: success if quorum reached, failure if not enough nodes respond within timeout

---

### 5. Replication Manager

Handles copying data to all replica nodes in parallel after the coordinator identifies the targets. Operates within a node, executing the actual write or delete on the local storage engine and propagating to peer replicas.

**Internal data structure:** none persistent. Stateless per operation.

#### Functions

**Replicate Write** - sends write operation to all replica nodes in parallel
- Input: list of replica node identifiers, key, value
- Output: list of acks from each replica node

**Replicate Delete** - sends delete operation to all replica nodes in parallel
- Input: list of replica node identifiers, key
- Output: list of acks from each replica node

**Execute Local Write** - calls local storage engine to perform the write
- Input: key, value
- Output: ack after WAL append and memtable write complete

**Execute Local Delete** - calls local storage engine to perform the delete
- Input: key
- Output: ack after operation is durably logged and memtable updated

**Sync On Rejoin** - when a node comes back online, pulls missing writes from peer replicas to catch up
- Input: node identifier, last known timestamp or version
- Output: list of missing key-value pairs, applied to local storage engine.

---

## Future Scope

- **DataFlow.GET_OPERATION.3** - the coordinator can perform quorum reads. The coordinator reads from multiple replicas, compares versions using a timestamp or version clock, and returns the most recent one. The current scope is reading from any single replica, which is faster but may return stale data occasionally.
- **FR:** Client should be able to take a snapshot of the db