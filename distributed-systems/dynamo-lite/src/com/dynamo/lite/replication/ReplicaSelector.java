package com.dynamo.lite.replication;

import java.util.List;

import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.hashing.ConsistentHashRing;

public class ReplicaSelector {

    private final ConsistentHashRing ring;

    public ReplicaSelector(
            ConsistentHashRing ring) {

        this.ring = ring;
    }

    public List<NodeInfo> selectReplicas(
            String key,
            int replicationFactor) {

        return ring.getPreferenceList(
                key,
                replicationFactor);
    }
}
