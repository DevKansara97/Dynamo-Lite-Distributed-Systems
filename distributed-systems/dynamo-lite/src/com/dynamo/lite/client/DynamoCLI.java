package com.dynamo.lite.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class DynamoCLI {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String connectedTo = null;

    public static void main(String[] args) {
        new DynamoCLI().run();
    }

    private void run() {

        Scanner scanner =
                new Scanner(System.in);

        System.out.println(
                "Dynamo-lite CLI. "
                + "Type 'help' for commands.");

        while (true) {

            System.out.print(
                    connectedTo != null
                    ? "dynamo@"
                      + connectedTo + "> "
                    : "dynamo> ");

            String line =
                    scanner.nextLine().trim();

            if (line.isEmpty()) continue;

            String[] parts =
                    line.split(" ", 3);
            String cmd =
                    parts[0].toLowerCase();

            switch (cmd) {

                case "connect" -> {
                    if (parts.length < 3) {
                        System.out.println(
                          "Usage: connect"
                          + " <host> <port>");
                        break;
                    }
                    connect(parts[1],
                            Integer.parseInt(
                                    parts[2]));
                }

                case "disconnect" ->
                        disconnect();

                case "put", "get",
                     "delete", "status",
                     "ping" -> {
                    if (socket == null) {
                        System.out.println(
                          "Not connected."
                          + " Use: connect"
                          + " <host> <port>");
                        break;
                    }
                    sendCommand(line);
                }

                case "help" ->
                        printHelp();

                case "quit", "exit" -> {
                    disconnect();
                    System.out.println(
                            "Goodbye.");
                    return;
                }

                default ->
                        System.out.println(
                            "Unknown: " + cmd
                            + ". Type 'help'.");
            }
        }
    }

    private void connect(
            String host, int port) {
        try {
            disconnect();
            socket = new Socket(host, port);
            out = new PrintWriter(
                    socket.getOutputStream(),
                    true);
            in = new BufferedReader(
                    new InputStreamReader(
                        socket.getInputStream()));
            connectedTo = host + ":" + port;
            System.out.println(
                    "Connected to "
                    + connectedTo);
        } catch (IOException e) {
            System.out.println(
                    "Connection failed: "
                    + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (socket != null) {
                socket.close();
                socket = null;
                out = null;
                in = null;
                connectedTo = null;
                System.out.println(
                        "Disconnected.");
            }
        } catch (IOException ignored) {
        }
    }

    private void sendCommand(String cmd) {
        try {
            out.println(cmd);
            String response = in.readLine();
            System.out.println(response);
        } catch (IOException e) {
            System.out.println(
                    "Error: " + e.getMessage());
            disconnect();
        }
    }

    private void printHelp() {
        System.out.println("""
                Commands:
                  connect <host> <port>
                  put <key> <value>
                  get <key>
                  delete <key>
                  status
                  ping
                  disconnect
                  quit / exit
                """);
    }
}
