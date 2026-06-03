package com.dynamo.lite.replication;

import java.util.List;

import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.hashing.ConsistentHashRing;

public class TestReplication {

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

        ReplicaSelector selector =
                new ReplicaSelector(ring);

        String[] keys = {
                "user1",
                "user2",
                "user3",
                "user4",
                "user5"
        };

        for (String key : keys) {

            System.out.println(
                    "\nKey = " + key);

            List<NodeInfo> replicas =
                    selector.selectReplicas(
                            key,
                            3);

            for (int i = 0;
                 i < replicas.size();
                 i++) {

                System.out.println(
                        "Replica "
                        + (i + 1)
                        + " -> "
                        + replicas.get(i)
                                .getNodeId());
            }
        }
    }
}
