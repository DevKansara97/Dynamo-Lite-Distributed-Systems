package com.dynamo.lite.cluster;

public class NodeInfo {

    private final String nodeId;
    private final String host;
    private final int port;

    public NodeInfo(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return nodeId + "(" + host + ":" + port + ")";
    }
}
