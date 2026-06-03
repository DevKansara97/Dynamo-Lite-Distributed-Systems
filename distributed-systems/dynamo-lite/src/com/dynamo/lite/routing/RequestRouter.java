package com.dynamo.lite.routing;

import com.dynamo.lite.protocol.CommandType;
import com.dynamo.lite.protocol.Request;
import com.dynamo.lite.protocol.Response;
import com.dynamo.lite.replication.ReplicationManager;
import com.dynamo.lite.storage.StorageEngine;
import com.dynamo.lite.util.DynamoLogger;

public class RequestRouter {

    private final String selfId;
    private final StorageEngine storage;
    private final ReplicationManager
            replicationManager;
    private final DynamoLogger logger;

    public RequestRouter(
            String selfId,
            StorageEngine storage,
            ReplicationManager
                    replicationManager,
            DynamoLogger logger) {

        this.selfId = selfId;
        this.storage = storage;
        this.replicationManager =
                replicationManager;
        this.logger = logger;
    }

    public String route(Request req) {

        return switch (req.getType()) {

            case PUT ->
                    replicationManager
                            .handlePut(
                                    req.getKey(),
                                    req.getValue());

            case GET ->
                    replicationManager
                            .handleGet(
                                    req.getKey());

            case DELETE ->
                    replicationManager
                            .handleDelete(
                                    req.getKey());

            case PING ->
                    Response.pong();

            case STATUS ->
                    Response.status(
                            "{\"node\":\""
                            + selfId
                            + "\","
                            + "\"keys\":"
                            + storage.size()
                            + "}");

            case INTERNAL_REPLICATE_PUT -> {
                storage.put(
                        req.getKey(),
                        req.getValue());
                yield Response.ack();
            }

            case INTERNAL_REPLICATE_DELETE -> {
                storage.delete(req.getKey());
                yield Response.ack();
            }

            case INTERNAL_REPLICATE_GET ->
                    storage.get(req.getKey())
                            .map(Response::value)
                            .orElse(
                              Response.notFound());

            case INTERNAL_PING ->
                    "INTERNAL PONG " + selfId;

            default ->
                    Response.error(
                            "UNKNOWN_COMMAND");
        };
    }
}
