package com.dynamo.lite.hashing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dynamo.lite.cluster.NodeInfo;

public class TestHashRing {

    public static void main(String[] args) {

        NodeInfo nodeA =
                new NodeInfo(
                        "NodeA",
                        "localhost",
                        7001);

        NodeInfo nodeB =
                new NodeInfo(
                        "NodeB",
                        "localhost",
                        7002);

        NodeInfo nodeC =
                new NodeInfo(
                        "NodeC",
                        "localhost",
                        7003);

        List<NodeInfo> nodes =
                List.of(
                        nodeA,
                        nodeB,
                        nodeC);

        ConsistentHashRing ring =
                new ConsistentHashRing(nodes);

        Map<String, Integer> counts =
                new HashMap<>();

        counts.put("NodeA", 0);
        counts.put("NodeB", 0);
        counts.put("NodeC", 0);

        int totalKeys = 10000;

        for (int i = 0; i < totalKeys; i++) {

            String key =
                    "user" + i;

            NodeInfo owner =
                    ring.getCoordinator(key);

            counts.put(
                    owner.getNodeId(),
                    counts.get(
                            owner.getNodeId())
                            + 1);
        }

        System.out.println(
                "\n===== KEY DISTRIBUTION =====");

        System.out.println(
                "NodeA -> "
                + counts.get("NodeA"));

        System.out.println(
                "NodeB -> "
                + counts.get("NodeB"));

        System.out.println(
                "NodeC -> "
                + counts.get("NodeC"));

        System.out.println(
                "============================");
    }
}
