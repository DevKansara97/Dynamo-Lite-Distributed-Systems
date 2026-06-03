package com.dynamo.lite.cluster;

import java.util.concurrent.ConcurrentHashMap;

public class ClusterState {

    private final ConcurrentHashMap<String, String> nodeStatus;

    public ClusterState() {
        nodeStatus = new ConcurrentHashMap<>();
    }

    public void markAlive(String nodeId) {
        nodeStatus.put(nodeId, "ALIVE");
    }

    public void markDown(String nodeId) {
        nodeStatus.put(nodeId, "DOWN");
    }

    public String getStatus(String nodeId) {
        return nodeStatus.getOrDefault(nodeId, "UNKNOWN");
    }

    public void printClusterState() {

        System.out.println("\n===== Cluster State =====");

        for (String nodeId : nodeStatus.keySet()) {
            System.out.println(
                    nodeId + " -> " +
                    nodeStatus.get(nodeId));
        }

        System.out.println("=========================\n");
    }
}

