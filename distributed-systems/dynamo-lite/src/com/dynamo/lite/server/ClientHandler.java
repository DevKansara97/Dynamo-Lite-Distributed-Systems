package com.dynamo.lite.server;

import com.dynamo.lite.protocol.RequestParser;
import com.dynamo.lite.storage.InMemoryStorageEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestParser parser;

    // Constructor accepts the shared engine and initializes a parser for this client
    public ClientHandler(Socket socket, InMemoryStorageEngine storageEngine) {
        this.socket = socket;
        this.parser = new RequestParser(storageEngine);
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String request;
            // Keep stream open for persistent, interactive client communication
            while ((request = in.readLine()) != null) {
                if (request.equalsIgnoreCase("exit")) {
                    out.println("Goodbye");
                    break;
                }
                
                // Process request using your provided parse logic
                String response = parser.parse(request);
                
                // Return data directly to the client network socket
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client handler exception: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("Client disconnected cleanly.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

