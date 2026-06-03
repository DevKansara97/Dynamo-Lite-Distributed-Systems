package com.dynamo.lite.protocol;

import com.dynamo.lite.storage.InMemoryStorageEngine;

public class RequestParser {

    private final InMemoryStorageEngine storage;

    public RequestParser(InMemoryStorageEngine storage) {
        this.storage = storage;
    }

    public String parse(String request) {

        if (request == null || request.isBlank()) {
            return "ERROR INVALID_COMMAND";
        }

        String[] parts = request.split(" ", 3);

        String command = parts[0].toUpperCase();

        switch (command) {

            case "PUT":

                if (parts.length < 3) {
                    return "ERROR INVALID_PUT";
                }

                return storage.put(parts[1], parts[2]);

            case "GET":

                if (parts.length < 2) {
                    return "ERROR INVALID_GET";
                }

                return storage.get(parts[1]);

            case "DELETE":

                if (parts.length < 2) {
                    return "ERROR INVALID_DELETE";
                }

                return storage.delete(parts[1]);
	    
	    case "PING":
		return "PONG";
 
            default:
                return "ERROR UNKNOWN_COMMAND";
        }
    }
}

