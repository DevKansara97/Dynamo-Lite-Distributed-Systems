package com.dynamo.lite.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.dynamo.lite.cluster.NodeInfo;
import com.dynamo.lite.config.DynamoConfig;

public class DynamoAdmin {

    // ANSI color codes
    private static final String GREEN =
            "\033[32m";
    private static final String RED =
            "\033[31m";
    private static final String YELLOW =
            "\033[33m";
    private static final String RESET =
            "\033[0m";
    private static final String CLEAR =
            "\033[H\033[2J";

    public static void main(String[] args)
            throws Exception {

        if (args.length < 1) {
            System.err.println(
                    "Usage: DynamoAdmin"
                    + " <config-file>");
            System.exit(1);
        }

        DynamoConfig config =
                new DynamoConfig(args[0]);

        // Self + peers
        List<NodeInfo> allNodes =
                new ArrayList<>();

        allNodes.add(new NodeInfo(
                config.getNodeId(),
                config.getNodeHost(),
                config.getNodePort()));

        allNodes.addAll(config.getPeers());

        System.out.println(
                "Dynamo Admin Dashboard"
                + " — polling every 2s"
                + " (Ctrl+C to exit)");

        while (true) {

            System.out.print(CLEAR);

            System.out.printf(
                    "%-14s %-10s %-10s%n",
                    "Node", "Status", "Keys");

            System.out.println(
                    "-".repeat(38));

            for (NodeInfo node : allNodes) {

                String statusLine =
                        pollNode(node);

                System.out.println(
                        statusLine);
            }

            System.out.println(
                    "-".repeat(38));
            System.out.printf(
                    "N=%d W=%d R=%d | "
                    + "Nodes: %d%n",
                    config.getReplicationN(),
                    config.getWriteQuorum(),
                    config.getReadQuorum(),
                    allNodes.size());

            Thread.sleep(2000);
        }
    }

    private static String pollNode(
            NodeInfo node) {

        try (Socket s = new Socket(
                node.getHost(),
                node.getPort())) {

            s.setSoTimeout(1500);

            PrintWriter out =
                    new PrintWriter(
                        s.getOutputStream(),
                        true);

            BufferedReader in =
                    new BufferedReader(
                        new InputStreamReader(
                            s.getInputStream()));

            out.println("STATUS");

            String response =
                    in.readLine();

            String keys = "?";

            if (response != null
                    && response.startsWith(
                            "STATUS ")) {
                String json =
                        response.substring(7);
                int idx = json.indexOf(
                        "\"keys\":");
                if (idx >= 0) {
                    keys = json.substring(
                            idx + 7)
                            .replace("}", "")
                            .trim();
                }
            }

            return String.format(
                    "%-14s %s%-10s%s %-10s",
                    node.getNodeId(),
                    GREEN, "ALIVE", RESET,
                    keys);

        } catch (IOException e) {

            return String.format(
                    "%-14s %s%-10s%s %-10s",
                    node.getNodeId(),
                    RED, "DOWN", RESET,
                    "--");
        }
    }
}
