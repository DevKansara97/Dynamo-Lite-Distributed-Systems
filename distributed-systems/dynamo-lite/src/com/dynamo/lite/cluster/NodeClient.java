package com.dynamo.lite.cluster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NodeClient {

    public String send(String host, int port, String command) {

        try (
            Socket socket = new Socket(host, port);

            PrintWriter out =
                    new PrintWriter(
                            socket.getOutputStream(),
                            true);

            BufferedReader in =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()))
        ) {

            out.println(command);

            return in.readLine();

        } catch (IOException e) {

            return "ERROR: " + e.getMessage();
        }
    }
}

