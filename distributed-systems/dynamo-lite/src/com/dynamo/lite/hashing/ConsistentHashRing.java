package com.dynamo.lite.hashing;

import java.util.List;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;

import com.dynamo.lite.cluster.NodeInfo;


public class ConsistentHashRing {

    private static final int VNODE_COUNT = 2;

    private final TreeMap<Long, NodeInfo> ring;

    public ConsistentHashRing(
            List<NodeInfo> nodes) {

        ring = new TreeMap<>();

        buildRing(nodes);
    }

    private void buildRing(
            List<NodeInfo> nodes) {

        for (NodeInfo node : nodes) {

            for (int i = 0; i < VNODE_COUNT; i++) {

                String vnodeId =
                        node.getNodeId()
                        + "-vnode-"
                        + i;

                long position =
                        HashFunction.hash(vnodeId);

                ring.put(position, node);

                System.out.println(
                        vnodeId
                        + " -> "
                        + position);
            }
        }
    }

    public NodeInfo getCoordinator(
            String key) {

        long keyHash =
                HashFunction.hash(key);

        var entry =
                ring.ceilingEntry(keyHash);

        if (entry == null) {

            return ring.firstEntry()
                    .getValue();
        }

        return entry.getValue();
    }

    public void printRing() {

        System.out.println(
                "\n===== HASH RING =====");

        for (var entry : ring.entrySet()) {

            System.out.println(
                    entry.getKey()
                    + " -> "
                    + entry.getValue()
                            .getNodeId());
        }

        System.out.println(
                "=====================\n");
    }
    
    public List<NodeInfo> getPreferenceList(
            String key,
            int replicationFactor) {

        List<NodeInfo> replicas =
                new ArrayList<>();

        Set<String> visitedNodes =
                new HashSet<>();

        long keyHash =
                HashFunction.hash(key);

        NavigableMap<Long, NodeInfo> tailMap =
                ring.tailMap(keyHash, true);

        for (NodeInfo node : tailMap.values()) {

            if (visitedNodes.add(node.getNodeId())) {

                replicas.add(node);

                if (replicas.size()
                        == replicationFactor) {

                    return replicas;
                }
            }
        }

        for (NodeInfo node : ring.values()) {

            if (visitedNodes.add(node.getNodeId())) {

                replicas.add(node);

                if (replicas.size()
                        == replicationFactor) {

                    return replicas;
                }
            }
        }

        return replicas;
    }
}
