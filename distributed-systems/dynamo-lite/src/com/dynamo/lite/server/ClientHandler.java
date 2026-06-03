package com.dynamo.lite.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.dynamo.lite.protocol.CommandType;
import com.dynamo.lite.protocol.Request;
import com.dynamo.lite.protocol.RequestParser;
import com.dynamo.lite.protocol.Response;
import com.dynamo.lite.storage.StorageEngine;
import com.dynamo.lite.util.DynamoLogger;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final StorageEngine storage;
    private final DynamoLogger logger;
    private final String nodeId;

    public ClientHandler(
            Socket socket,
            StorageEngine storage,
            String nodeId,
            DynamoLogger logger) {

        this.socket = socket;
        this.storage = storage;
        this.nodeId = nodeId;
        this.logger = logger;
    }

    @Override
    public void run() {

        String clientAddr =
                socket.getRemoteSocketAddress()
                        .toString();

        logger.info("Client connected: "
                + clientAddr);

        try (
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream()));
                PrintWriter out =
                        new PrintWriter(
                                socket.getOutputStream(),
                                true)
        ) {
            String line;

            while ((line = in.readLine())
                    != null) {

                Request req =
                        RequestParser.parse(line);

                String response =
                        handleRequest(req);

                out.println(response);
            }

        } catch (IOException e) {
            logger.warn("Client disconnected: "
                    + clientAddr
                    + " ("
                    + e.getMessage()
                    + ")");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String handleRequest(Request req) {

        return switch (req.getType()) {

            case PUT -> {
                String key = req.getKey();
                String value = req.getValue();

                if (key == null
                        || key.isBlank()
                        || key.length() > 256) {
                    yield Response.error(
                            "INVALID_KEY");
                }

                if (value == null
                        || value.isEmpty()) {
                    yield Response.error(
                            "INVALID_VALUE");
                }

                storage.put(key, value);
                logger.info("PUT "
                        + key + " = " + value);
                yield Response.ok();
            }

            case GET -> {
                String key = req.getKey();

                if (key == null
                        || key.isBlank()) {
                    yield Response.error(
                            "INVALID_KEY");
                }

                yield storage.get(key)
                        .map(Response::value)
                        .orElse(
                                Response.notFound());
            }

            case DELETE -> {
                String key = req.getKey();

                if (key == null
                        || key.isBlank()) {
                    yield Response.error(
                            "INVALID_KEY");
                }

                yield storage.delete(key)
                        ? Response.ok()
                        : Response.notFound();
            }

            case PING ->
                    Response.pong();

            case STATUS ->
                    Response.status(
                            "{\"node\":\""
                            + nodeId
                            + "\","
                            + "\"keys\":"
                            + storage.size()
                            + "}");

            // Internal replication —
            // handled directly here for now
            case INTERNAL_REPLICATE_PUT -> {
                String key = req.getKey();
                String value = req.getValue();
                if (key != null
                        && value != null) {
                    storage.put(key, value);
                    logger.info(
                            "REPLICATED PUT "
                            + key);
                }
                yield Response.ack();
            }

            case INTERNAL_REPLICATE_DELETE -> {
                String key = req.getKey();
                if (key != null) {
                    storage.delete(key);
                    logger.info(
                            "REPLICATED DELETE "
                            + key);
                }
                yield Response.ack();
            }

            case INTERNAL_REPLICATE_GET -> {
                String key = req.getKey();
                if (key == null) {
                    yield Response.error(
                            "INVALID_KEY");
                }
                yield storage.get(key)
                        .map(Response::value)
                        .orElse(
                                Response.notFound());
            }

            case INTERNAL_PING ->
                    "INTERNAL PONG "
                    + nodeId;

            default ->
                    Response.error(
                            "UNKNOWN_COMMAND");
        };
    }
}
