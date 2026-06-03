package com.dynamo.lite;

import java.util.List;
import com.dynamo.lite.cluster.ClusterState;
import com.dynamo.lite.cluster.HeartbeatManager;
import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.cluster.ReplicaClient;

import com.dynamo.lite.config.DynamoConfig;
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

        logger.info("Starting node: "
                + config.getNodeId()
                + " on port "
                + config.getNodePort());

        StorageEngine storage =
                new InMemoryStorageEngine();
                
        // Initialize cluster state
        List<NodeInfo> peers = config.getPeers();

        ClusterState clusterState =
                new ClusterState(
                        config.getHeartbeatMissThreshold());

        for (NodeInfo peer : peers) {
            clusterState.addNode(peer.getNodeId());
        }

        // Start heartbeat
        ReplicaClient replicaClient =
                new ReplicaClient(
                        config.getHeartbeatTimeoutMs(),
                        logger);

        HeartbeatManager heartbeat =
                new HeartbeatManager(
                        config.getNodeId(),
                        peers,
                        clusterState,
                        replicaClient,
                        config.getHeartbeatIntervalMs(),
                        logger);

        heartbeat.start();

        TcpServer server = new TcpServer(
                config.getNodePort(),
                storage,
                config.getNodeId(),
                config.getClientThreadPoolSize(),
                logger);

        Thread serverThread =
                new Thread(server,
                        "tcp-server");
        serverThread.setDaemon(false);
        serverThread.start();

        logger.info("Node "
                + config.getNodeId()
                + " is RUNNING. "
                + "Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    logger.info("Shutting down "
                            + config.getNodeId());
                    server.stop();
                }));
    }
}
