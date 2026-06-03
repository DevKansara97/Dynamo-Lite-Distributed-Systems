package com.dynamo.lite.replication;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.dynamo.lite.cluster.ClusterState;
import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.cluster.ReplicaClient;
import com.dynamo.lite.hashing.ConsistentHashRing;
import com.dynamo.lite.protocol.Response;
import com.dynamo.lite.replication.HintedHandoffStore.HintedEntry;
import com.dynamo.lite.storage.StorageEngine;
import com.dynamo.lite.util.DynamoLogger;

public class ReplicationManager {

    private final String selfId;
    private final StorageEngine storage;
    private final ConsistentHashRing ring;
    private final ClusterState clusterState;
    private final ReplicaClient replicaClient;
    private final HintedHandoffStore hintStore;
    private final int replicationN;
    private final int writeQuorum;
    private final int readQuorum;
    private final int timeoutMs;
    private final DynamoLogger logger;

    private final ExecutorService
            replicationPool =
            Executors.newFixedThreadPool(4,
                    r -> new Thread(r,
                            "replication-worker"));

    public ReplicationManager(
            String selfId,
            StorageEngine storage,
            ConsistentHashRing ring,
            ClusterState clusterState,
            ReplicaClient replicaClient,
            HintedHandoffStore hintStore,
            int replicationN,
            int writeQuorum,
            int readQuorum,
            int timeoutMs,
            DynamoLogger logger) {

        this.selfId = selfId;
        this.storage = storage;
        this.ring = ring;
        this.clusterState = clusterState;
        this.replicaClient = replicaClient;
        this.hintStore = hintStore;
        this.replicationN = replicationN;
        this.writeQuorum = writeQuorum;
        this.readQuorum = readQuorum;
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    // ---- PUT (quorum write) ----

    public String handlePut(
            String key, String value) {

        List<NodeInfo> preferenceList =
                ring.getPreferenceList(
                        key, replicationN);

        // Local write if self is in list
        boolean selfInList = preferenceList
                .stream()
                .anyMatch(n -> n.getNodeId()
                        .equals(selfId));

        int acks = 0;

        if (selfInList) {
            storage.put(key, value);
            acks = 1;
        }

        // Fan out to other replicas
        List<NodeInfo> remotes = preferenceList
                .stream()
                .filter(n -> !n.getNodeId()
                        .equals(selfId))
                .toList();

        List<Future<Boolean>> futures =
                new ArrayList<>();

        for (NodeInfo replica : remotes) {

            if (clusterState.isDown(
                    replica.getNodeId())) {

                // Hinted handoff
                hintStore.add(
                        replica.getNodeId(),
                        new HintedEntry(
                                key, value,
                                "PUT"));
                continue;
            }

            final NodeInfo r = replica;
            futures.add(replicationPool
                    .submit(() -> replicaClient
                            .send(r,
                                    "INTERNAL"
                                    + " REPLICATE"
                                    + " PUT "
                                    + key + " "
                                    + value)
                            .map("ACK"::equals)
                            .orElse(false)));
        }

        for (Future<Boolean> f : futures) {
            try {
                if (f.get(timeoutMs,
                        TimeUnit.MILLISECONDS)) {
                    acks++;
                }
            } catch (Exception e) {
                logger.warn(
                        "Replication ack "
                        + "timeout for PUT "
                        + key);
            }
            if (acks >= writeQuorum) break;
        }

        if (acks >= writeQuorum) {
            logger.info("PUT " + key
                    + " acked by "
                    + acks + " nodes");
            return Response.ok();
        }

        logger.warn("PUT " + key
                + " WRITE_FAILURE"
                + " (only " + acks + " acks)");
        return Response.error("WRITE_FAILURE");
    }

    // ---- GET (quorum read) ----

    public String handleGet(String key) {

        List<NodeInfo> preferenceList =
                ring.getPreferenceList(
                        key, replicationN);

        List<String> responses =
                new ArrayList<>();

        // Check self
        boolean selfInList = preferenceList
                .stream()
                .anyMatch(n -> n.getNodeId()
                        .equals(selfId));

        if (selfInList) {
            storage.get(key)
                    .ifPresent(responses::add);
        }

        // Query remotes
        List<NodeInfo> aliveRemotes =
                preferenceList.stream()
                        .filter(n ->
                                !n.getNodeId()
                                .equals(selfId)
                                && clusterState
                                .isAlive(
                                 n.getNodeId()))
                        .toList();

        List<Future<Optional<String>>>
                futures = new ArrayList<>();

        for (NodeInfo replica : aliveRemotes) {

            final NodeInfo r = replica;
            futures.add(replicationPool
                    .submit(() ->
                            replicaClient.send(r,
                                    "INTERNAL"
                                    + " REPLICATE"
                                    + " GET "
                                    + key)
                            .map(resp ->
                                    resp.startsWith(
                                     "VALUE ")
                                    ? Optional.of(
                                     resp.substring(
                                      6))
                                    : Optional
                                     .<String>empty())
                            .orElse(
                             Optional.empty())));
        }

        for (Future<Optional<String>>
                f : futures) {
            try {
                f.get(timeoutMs,
                        TimeUnit.MILLISECONDS)
                        .ifPresent(
                                responses::add);
            } catch (Exception e) {
                logger.warn(
                        "Read replica timeout"
                        + " for GET " + key);
            }
            if (responses.size()
                    >= readQuorum) break;
        }

        if (responses.isEmpty()) {
            return Response.notFound();
        }

        if (responses.size() < readQuorum) {
            logger.warn("GET " + key
                    + " READ_FAILURE"
                    + " (only "
                    + responses.size()
                    + " responses)");
            return Response.error(
                    "READ_FAILURE");
        }

        // Return majority value
        // (simple: return first for now;
        //  read repair can be added later)
        return Response.value(responses.get(0));
    }

    // ---- DELETE (quorum delete) ----

    public String handleDelete(String key) {

        List<NodeInfo> preferenceList =
                ring.getPreferenceList(
                        key, replicationN);

        boolean found = false;
        int acks = 0;

        boolean selfInList = preferenceList
                .stream()
                .anyMatch(n -> n.getNodeId()
                        .equals(selfId));

        if (selfInList) {
            found = storage.delete(key);
            acks = 1;
        }

        List<NodeInfo> remotes = preferenceList
                .stream()
                .filter(n -> !n.getNodeId()
                        .equals(selfId)
                        && clusterState.isAlive(
                                n.getNodeId()))
                .toList();

        List<Future<Boolean>> futures =
                new ArrayList<>();

        for (NodeInfo replica : remotes) {
            final NodeInfo r = replica;
            futures.add(replicationPool
                    .submit(() -> replicaClient
                            .send(r,
                                    "INTERNAL"
                                    + " REPLICATE"
                                    + " DELETE "
                                    + key)
                            .map("ACK"::equals)
                            .orElse(false)));
        }

        for (Future<Boolean> f : futures) {
            try {
                if (f.get(timeoutMs,
                        TimeUnit.MILLISECONDS)) {
                    acks++;
                    found = true;
                }
            } catch (Exception e) {
                logger.warn(
                        "Delete ack timeout"
                        + " for " + key);
            }
            if (acks >= writeQuorum) break;
        }

        if (!found) return Response.notFound();
        if (acks >= writeQuorum) {
            return Response.ok();
        }
        return Response.error(
                "DELETE_FAILURE");
    }
}
