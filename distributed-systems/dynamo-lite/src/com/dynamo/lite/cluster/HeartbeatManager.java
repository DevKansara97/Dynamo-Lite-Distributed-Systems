package com.dynamo.lite.cluster;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatManager {

    private final NodeInfo targetNode;

    private final NodeClient client;

    private final ClusterState clusterState;

    private final ScheduledExecutorService scheduler;

    public HeartbeatManager(
            NodeInfo targetNode,
            ClusterState clusterState) {

        this.targetNode = targetNode;
        this.clusterState = clusterState;

        this.client = new NodeClient();

        this.scheduler =
                Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {

        scheduler.scheduleAtFixedRate(() -> {

            String response =
                    client.send(
                            targetNode.getHost(),
                            targetNode.getPort(),
                            "PING"
                    );

            if ("PONG".equals(response)) {

                clusterState.markAlive(
                        targetNode.getNodeId());

                System.out.println(
                        "[Heartbeat] "
                                + targetNode.getNodeId()
                                + " is ALIVE");

            } else {

                clusterState.markDown(
                        targetNode.getNodeId());

                System.out.println(
                        "[Heartbeat] "
                                + targetNode.getNodeId()
                                + " is DOWN");
            }

        }, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }
}
