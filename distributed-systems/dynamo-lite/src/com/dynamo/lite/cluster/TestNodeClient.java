package com.dynamo.lite.cluster;
import com.dynamo.lite.cluster.NodeClient;

public class TestNodeClient {

    public static void main(String[] args) {

        NodeClient client = new NodeClient();

        String response =
                client.send(
                        "localhost",
                        7002,
                        "get name");

        System.out.println("Response = " + response);
    }
}

