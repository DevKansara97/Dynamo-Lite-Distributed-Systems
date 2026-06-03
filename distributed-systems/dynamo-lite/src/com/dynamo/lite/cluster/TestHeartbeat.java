package com.dynamo.lite.cluster;

public class TestHeartbeat {

    public static void main(String[] args) {

        NodeInfo nodeB =
                new NodeInfo(
                        "NodeB",
                        "localhost",
                        7002
                );

        ClusterState clusterState =
                new ClusterState();

        HeartbeatManager heartbeatManager =
                new HeartbeatManager(
                        nodeB,
                        clusterState
                );

        heartbeatManager.start();
    }
}
