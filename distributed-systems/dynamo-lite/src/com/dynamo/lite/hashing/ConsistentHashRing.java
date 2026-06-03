package com.dynamo.lite.hashing;

import java.util.List;
import java.util.TreeMap;

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
}
