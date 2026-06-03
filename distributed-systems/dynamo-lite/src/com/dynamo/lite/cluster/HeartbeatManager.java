package com.dynamo.lite.cluster;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.dynamo.lite.util.DynamoLogger;

public class HeartbeatManager {

    private final String selfId;
    private final List<NodeInfo> peers;
    private final ClusterState clusterState;
    private final ReplicaClient replicaClient;
    private final int intervalMs;
    private final DynamoLogger logger;

    private final ScheduledExecutorService
            scheduler = Executors
            .newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r,
                        "heartbeat-scheduler");
                t.setDaemon(true);
                return t;
            });

    public HeartbeatManager(
            String selfId,
            List<NodeInfo> peers,
            ClusterState clusterState,
            ReplicaClient replicaClient,
            int intervalMs,
            DynamoLogger logger) {

        this.selfId = selfId;
        this.peers = peers;
        this.clusterState = clusterState;
        this.replicaClient = replicaClient;
        this.intervalMs = intervalMs;
        this.logger = logger;
    }

    public void start() {

        scheduler.scheduleAtFixedRate(
                this::pingAllPeers,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS);

        logger.info(
                "HeartbeatManager started"
                + " (interval="
                + intervalMs + "ms)");
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void pingAllPeers() {

        for (NodeInfo peer : peers) {

            String pong = replicaClient
                    .send(peer,
                            "INTERNAL PING "
                            + selfId)
                    .orElse(null);

            if (pong != null
                    && pong.startsWith(
                            "INTERNAL PONG")) {

                boolean wasDown =
                        clusterState.isDown(
                                peer.getNodeId());

                clusterState.markAlive(
                        peer.getNodeId());

                if (wasDown) {
                    logger.info(peer.getNodeId()
                            + " recovered → ALIVE");
                }

            } else {

                clusterState.recordMiss(
                        peer.getNodeId());

                logger.warn(peer.getNodeId()
                        + " missed heartbeat → "
                        + clusterState.getStatus(
                                peer.getNodeId()));
            }
        }
    }
}
