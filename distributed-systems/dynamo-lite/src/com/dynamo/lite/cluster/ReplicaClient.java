package com.dynamo.lite.cluster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Optional;

import com.dynamo.lite.util.DynamoLogger;

public class ReplicaClient {

    private final int timeoutMs;
    private final DynamoLogger logger;

    public ReplicaClient(
            int timeoutMs,
            DynamoLogger logger) {
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    public Optional<String> send(
            NodeInfo target,
            String command) {

        try (Socket s = new Socket(
                target.getHost(),
                target.getPort())) {

            s.setSoTimeout(timeoutMs);

            PrintWriter out =
                    new PrintWriter(
                            s.getOutputStream(),
                            true);

            BufferedReader in =
                    new BufferedReader(
                            new InputStreamReader(
                                    s.getInputStream()));

            out.println(command);

            return Optional.ofNullable(
                    in.readLine());

        } catch (IOException e) {
            logger.warn(
                    "ReplicaClient → "
                    + target.getNodeId()
                    + " failed: "
                    + e.getMessage());
            return Optional.empty();
        }
    }
}
