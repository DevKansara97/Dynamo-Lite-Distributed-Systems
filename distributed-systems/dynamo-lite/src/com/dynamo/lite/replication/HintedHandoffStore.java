package com.dynamo.lite.replication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.cluster.ReplicaClient;
import com.dynamo.lite.util.DynamoLogger;

public class HintedHandoffStore {

    public static class HintedEntry {
        public final String key;
        public final String value;
        public final String operation;
        public final long timestamp;

        public HintedEntry(
                String key,
                String value,
                String operation) {
            this.key = key;
            this.value = value;
            this.operation = operation;
            this.timestamp =
                    System.currentTimeMillis();
        }
    }

    private static final int MAX_HINTS = 1000;

    private final ConcurrentHashMap<String,
            CopyOnWriteArrayList<HintedEntry>>
            store = new ConcurrentHashMap<>();

    private final DynamoLogger logger;

    public HintedHandoffStore(
            DynamoLogger logger) {
        this.logger = logger;
    }

    public void add(
            String targetNodeId,
            HintedEntry entry) {

        store.computeIfAbsent(
                targetNodeId,
                k -> new CopyOnWriteArrayList<>());

        CopyOnWriteArrayList<HintedEntry> hints
                = store.get(targetNodeId);

        if (hints.size() < MAX_HINTS) {
            hints.add(entry);
            logger.info("Hinted entry stored"
                    + " for " + targetNodeId
                    + " key=" + entry.key);
        } else {
            logger.warn("Hint store full for "
                    + targetNodeId
                    + " — dropping hint");
        }
    }

    public void flush(
            String targetNodeId,
            NodeInfo targetNode,
            ReplicaClient replicaClient) {

        CopyOnWriteArrayList<HintedEntry>
                hints = store.get(targetNodeId);

        if (hints == null
                || hints.isEmpty()) return;

        List<HintedEntry> toFlush =
                new ArrayList<>(hints);

        logger.info("Flushing "
                + toFlush.size()
                + " hints to "
                + targetNodeId);

        int sent = 0;

        for (HintedEntry e : toFlush) {

            String cmd;

            if ("PUT".equals(e.operation)) {
                cmd = "INTERNAL REPLICATE PUT "
                        + e.key + " " + e.value;
            } else {
                cmd = "INTERNAL REPLICATE DELETE "
                        + e.key;
            }

            boolean ok = replicaClient
                    .send(targetNode, cmd)
                    .map("ACK"::equals)
                    .orElse(false);

            if (ok) {
                hints.remove(e);
                sent++;
            }
        }

        logger.info("Flushed " + sent
                + "/" + toFlush.size()
                + " hints to "
                + targetNodeId);
    }

    public int size(String targetNodeId) {
        CopyOnWriteArrayList<HintedEntry>
                hints = store.get(targetNodeId);
        return hints == null ? 0 : hints.size();
    }
}
