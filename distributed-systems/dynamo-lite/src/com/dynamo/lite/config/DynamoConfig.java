package com.dynamo.lite.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.dynamo.lite.cluster.NodeInfo;

public class DynamoConfig {

    private final Properties props;

    public DynamoConfig(String configFilePath) {
        props = new Properties();
        try (FileInputStream fis =
                     new FileInputStream(configFilePath)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load config: "
                    + configFilePath, e);
        }
    }

    public String getNodeId() {
        return props.getProperty("node.id");
    }

    public String getNodeHost() {
        return props.getProperty("node.host");
    }

    public int getNodePort() {
        return Integer.parseInt(
                props.getProperty("node.port"));
    }

    public List<NodeInfo> getPeers() {
        String peersStr =
                props.getProperty("peers", "");
        List<NodeInfo> peers = new ArrayList<>();
        if (peersStr.isBlank()) return peers;

        for (String entry : peersStr.split(",")) {
            String[] parts = entry.trim().split(":");
            peers.add(new NodeInfo(
                    parts[0],
                    parts[1],
                    Integer.parseInt(parts[2])));
        }
        return peers;
    }

    public int getReplicationN() {
        return Integer.parseInt(
                props.getProperty(
                        "replication.n", "3"));
    }

    public int getWriteQuorum() {
        return Integer.parseInt(
                props.getProperty(
                        "replication.w", "2"));
    }

    public int getReadQuorum() {
        return Integer.parseInt(
                props.getProperty(
                        "replication.r", "2"));
    }

    public int getVnodeCount() {
        return Integer.parseInt(
                props.getProperty(
                        "vnodes.count", "150"));
    }

    public int getHeartbeatIntervalMs() {
        return Integer.parseInt(
                props.getProperty(
                        "heartbeat.interval.ms",
                        "1000"));
    }

    public int getHeartbeatTimeoutMs() {
        return Integer.parseInt(
                props.getProperty(
                        "heartbeat.timeout.ms",
                        "2000"));
    }

    public int getHeartbeatMissThreshold() {
        return Integer.parseInt(
                props.getProperty(
                        "heartbeat.miss.threshold",
                        "3"));
    }

    public int getClientThreadPoolSize() {
        return Integer.parseInt(
                props.getProperty(
                        "client.thread.pool.size",
                        "50"));
    }

    public int getClientIdleTimeoutMs() {
        return Integer.parseInt(
                props.getProperty(
                        "client.idle.timeout.ms",
                        "30000"));
    }

    public int getReplicationTimeoutMs() {
        return Integer.parseInt(
                props.getProperty(
                        "replication.timeout.ms",
                        "5000"));
    }
}
