package com.dynamo.lite.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class KVClient {

    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 7001;

    public static void main(String[] args) {
        System.out.println("Connecting to Dynamo-Lite KV Server at " + SERVER_IP + ":" + SERVER_PORT + "...");
        
        try (
            // Establish a dedicated TCP socket connection to the storage cluster
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader networkIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Successfully connected! Available commands: PUT <k> <v>, GET <k>, DELETE <k>");
            System.out.println("Type 'exit' to terminate connection.\n");

            String userInput;
            System.out.print("kv-store> ");
            
            // Loop blocks waiting for your console input
            while ((userInput = consoleIn.readLine()) != null) {
                
                // Trim trailing spaces but don't drop empty commands
                if (userInput.isBlank()) {
                    System.out.print("kv-store> ");
                    continue;
                }

                // Forward the raw query line to the KVServer over the wire
                out.println(userInput);

                // If user types exit, break out immediately after signaling server
                if (userInput.equalsIgnoreCase("exit")) {
                    // Read final graceful disconnect message from server ("Goodbye")
                    String goodbye = networkIn.readLine();
                    System.out.println("Server response: " + goodbye);
                    break;
                }

                // Request-Response Block: Wait synchronously for the engine response
                String serverResponse = networkIn.readLine();
                
                if (serverResponse == null) {
                    System.out.println("Error: Server disconnected abruptly.");
                    break;
                }

                // Render out the exact output returned by the storage engine
                System.out.println("-> " + serverResponse);
                System.out.print("kv-store> ");
            }

        } catch (IOException e) {
            System.err.println("Database Client Connection Error: " + e.getMessage());
        }
        
        System.out.println("Client closed down safely.");
    }
}

