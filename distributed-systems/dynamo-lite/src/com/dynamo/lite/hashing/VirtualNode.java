package com.dynamo.lite.hashing;

import com.dynamo.lite.cluster.NodeInfo;

public class VirtualNode {

    private final String vnodeId;
    private final long position;
    private final NodeInfo physicalNode;

    public VirtualNode(
            String vnodeId,
            long position,
            NodeInfo physicalNode) {

        this.vnodeId = vnodeId;
        this.position = position;
        this.physicalNode = physicalNode;
    }

    public String getVnodeId() {
        return vnodeId;
    }

    public long getPosition() {
        return position;
    }

    public NodeInfo getPhysicalNode() {
        return physicalNode;
    }

    @Override
    public String toString() {

        return vnodeId +
                " -> " +
                position +
                " -> " +
                physicalNode.getNodeId();
    }
}
