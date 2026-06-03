package com.dynamo.lite.cluster;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ClusterState {

    private final ConcurrentHashMap<String,
            NodeStatus> statuses =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String,
            AtomicInteger> missCounters =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String,
            AtomicLong> lastSeen =
            new ConcurrentHashMap<>();

    private final int missThreshold;

    public ClusterState(
            int missThreshold) {
        this.missThreshold = missThreshold;
    }

    public void addNode(String nodeId) {
        statuses.put(nodeId,
                NodeStatus.ALIVE);
        missCounters.put(nodeId,
                new AtomicInteger(0));
        lastSeen.put(nodeId,
                new AtomicLong(
                        System.currentTimeMillis()));
    }

    public void markAlive(String nodeId) {
        statuses.put(nodeId,
                NodeStatus.ALIVE);
        missCounters.get(nodeId)
                .set(0);
        lastSeen.get(nodeId)
                .set(System.currentTimeMillis());
    }

    public void recordMiss(String nodeId) {

        AtomicInteger counter =
                missCounters.get(nodeId);

        if (counter == null) return;

        int misses = counter.incrementAndGet();

        if (misses == 1) {
            statuses.put(nodeId,
                    NodeStatus.SUSPECTED);
        } else if (misses
                >= missThreshold) {
            statuses.put(nodeId,
                    NodeStatus.DOWN);
        }
    }

    public boolean isAlive(String nodeId) {
        NodeStatus s =
                statuses.get(nodeId);
        return s == NodeStatus.ALIVE
                || s == NodeStatus.SUSPECTED;
    }

    public boolean isDown(String nodeId) {
        NodeStatus s =
                statuses.get(nodeId);
        return s == null
                || s == NodeStatus.DOWN;
    }

    public NodeStatus getStatus(
            String nodeId) {
        return statuses.getOrDefault(
                nodeId,
                NodeStatus.DOWN);
    }

    public Map<String, NodeStatus>
            getAllStatuses() {
        return Map.copyOf(statuses);
    }
}
