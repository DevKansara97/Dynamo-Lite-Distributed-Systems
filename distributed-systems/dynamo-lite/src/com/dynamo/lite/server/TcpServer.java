package com.dynamo.lite.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dynamo.lite.routing.RequestRouter;
import com.dynamo.lite.util.DynamoLogger;

public class TcpServer implements Runnable {

    private final int port;
    private final RequestRouter router;
    private final DynamoLogger logger;
    private final ExecutorService pool;

    private volatile boolean running = true;

    public TcpServer(
            int port,
            RequestRouter router,
            int threadPoolSize,
            DynamoLogger logger) {

        this.port = port;
        this.router = router;
        this.logger = logger;
        this.pool = Executors
                .newFixedThreadPool(
                        threadPoolSize);
    }

    @Override
    public void run() {

        try (ServerSocket serverSocket =
                     new ServerSocket(port)) {

            logger.info(
                    "TCP server listening"
                    + " on port " + port);

            while (running) {

                Socket client =
                        serverSocket.accept();

                client.setSoTimeout(30_000);

                pool.submit(
                        new ClientHandler(
                                client,
                                router,
                                logger));
            }

        } catch (IOException e) {
            if (running) {
                logger.error(
                        "TCP server error: "
                        + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        pool.shutdown();
    }
}
