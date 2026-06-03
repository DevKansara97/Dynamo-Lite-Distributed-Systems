package com.dynamo.lite.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.dynamo.lite.protocol.Request;
import com.dynamo.lite.protocol.RequestParser;
import com.dynamo.lite.routing.RequestRouter;
import com.dynamo.lite.util.DynamoLogger;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final RequestRouter router;
    private final DynamoLogger logger;

    public ClientHandler(
            Socket socket,
            RequestRouter router,
            DynamoLogger logger) {

        this.socket = socket;
        this.router = router;
        this.logger = logger;
    }

    @Override
    public void run() {

        String clientAddr =
                socket.getRemoteSocketAddress()
                        .toString();

        logger.info(
                "Client connected: "
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
                        router.route(req);

                out.println(response);
            }

        } catch (IOException e) {
            logger.warn(
                    "Client disconnected: "
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
}
