package com.dynamo.lite.server;

import com.dynamo.lite.storage.InMemoryStorageEngine;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KVServer {	
    
    // Core thread pool to handle concurrent requests
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {

	int port = 7001;
	if (args.length > 0) {
		port = Integer.parseInt(args[0]);
	}

        // CRITICAL: Create ONE single database instance shared by all threads
        InMemoryStorageEngine storageEngine = new InMemoryStorageEngine();
        
        System.out.println("KV Storage Server started on port " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Blocks until a new database client connects
                Socket socket = serverSocket.accept();
                System.out.println("New client connected from: " + socket.getRemoteSocketAddress());

                // Pass both the socket AND the shared storage engine to the handler
                pool.execute(new ClientHandler(socket, storageEngine));
            }
        } catch (IOException e) {
            System.err.println("Server execution error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}

