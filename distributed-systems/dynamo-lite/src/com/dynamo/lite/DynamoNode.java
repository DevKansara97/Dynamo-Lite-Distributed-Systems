package com.dynamo.lite;

import java.util.ArrayList;
import java.util.List;

import com.dynamo.lite.cluster.ClusterState;
import com.dynamo.lite.cluster.HeartbeatManager;
import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.cluster.ReplicaClient;
import com.dynamo.lite.config.DynamoConfig;
import com.dynamo.lite.hashing.ConsistentHashRing;
import com.dynamo.lite.replication.HintedHandoffStore;
import com.dynamo.lite.replication.ReplicationManager;
import com.dynamo.lite.routing.RequestRouter;
import com.dynamo.lite.server.TcpServer;
import com.dynamo.lite.storage.InMemoryStorageEngine;
import com.dynamo.lite.storage.StorageEngine;
import com.dynamo.lite.util.DynamoLogger;

public class DynamoNode {

    public static void main(String[] args)
            throws Exception {

        if (args.length < 1) {
            System.err.println(
                    "Usage: DynamoNode"
                    + " <config-file>");
            System.exit(1);
        }

        DynamoConfig config =
                new DynamoConfig(args[0]);

        DynamoLogger logger =
                new DynamoLogger(
                        config.getNodeId());

        logger.info("Starting "
                + config.getNodeId()
                + " on port "
                + config.getNodePort());

        // Storage
        StorageEngine storage =
                new InMemoryStorageEngine();

        // Cluster state
        List<NodeInfo> peers =
                config.getPeers();

        ClusterState clusterState =
                new ClusterState(
                        config
                        .getHeartbeatMissThreshold());

        for (NodeInfo peer : peers) {
            clusterState.addNode(
                    peer.getNodeId());
        }

        // Hash ring — self + peers
        List<NodeInfo> allNodes =
                new ArrayList<>();

        allNodes.add(new NodeInfo(
                config.getNodeId(),
                config.getNodeHost(),
                config.getNodePort()));

        allNodes.addAll(peers);

        ConsistentHashRing ring =
                new ConsistentHashRing(
                        allNodes);

        // Replica client
        ReplicaClient replicaClient =
                new ReplicaClient(
                        config
                        .getReplicationTimeoutMs(),
                        logger);

        // Hinted handoff store
        HintedHandoffStore hintStore =
                new HintedHandoffStore(logger);

        // Replication manager
        ReplicationManager replicationManager =
                new ReplicationManager(
                        config.getNodeId(),
                        storage,
                        ring,
                        clusterState,
                        replicaClient,
                        hintStore,
                        config.getReplicationN(),
                        config.getWriteQuorum(),
                        config.getReadQuorum(),
                        config
                        .getReplicationTimeoutMs(),
                        logger);

        // Request router
        RequestRouter router =
                new RequestRouter(
                        config.getNodeId(),
                        storage,
                        replicationManager,
                        logger);

        // Heartbeat
        HeartbeatManager heartbeat =
                new HeartbeatManager(
                        config.getNodeId(),
                        peers,
                        clusterState,
                        replicaClient,
                        config
                        .getHeartbeatIntervalMs(),
                        logger);

        heartbeat.start();

        // TCP server
        TcpServer server = new TcpServer(
              config.getNodePort(),
              router,
              config.getClientThreadPoolSize(),
              logger);

        Thread serverThread =
                new Thread(server,
                        "tcp-server");
        serverThread.setDaemon(false);
        serverThread.start();

        logger.info("Node "
                + config.getNodeId()
                + " is RUNNING.");

        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                () -> {
                    logger.info(
                            "Shutting down "
                            + config.getNodeId());
                    heartbeat.stop();
                    server.stop();
                }));
    }
}
