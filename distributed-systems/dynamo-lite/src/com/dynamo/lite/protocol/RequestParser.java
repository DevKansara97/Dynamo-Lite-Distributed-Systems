package com.dynamo.lite.protocol;

public class RequestParser {

    public static Request parse(String line) {

        if (line == null
                || line.isBlank()) {
            return new Request(
                    CommandType.UNKNOWN,
                    null, null, false);
        }

        String trimmed = line.trim();

        // Internal node-to-node messages
        if (trimmed.startsWith("INTERNAL ")) {
            return parseInternal(trimmed);
        }

        // Client messages
        String[] parts = trimmed.split(" ", 3);
        String cmd = parts[0].toUpperCase();

        return switch (cmd) {

            case "PUT" -> {
                if (parts.length < 3) yield new Request(
                        CommandType.UNKNOWN,
                        null, null, false);
                yield new Request(
                        CommandType.PUT,
                        parts[1], parts[2], false);
            }

            case "GET" -> {
                if (parts.length < 2) yield new Request(
                        CommandType.UNKNOWN,
                        null, null, false);
                yield new Request(
                        CommandType.GET,
                        parts[1], null, false);
            }

            case "DELETE" -> {
                if (parts.length < 2) yield new Request(
                        CommandType.UNKNOWN,
                        null, null, false);
                yield new Request(
                        CommandType.DELETE,
                        parts[1], null, false);
            }

            case "PING" -> new Request(
                    CommandType.PING,
                    null, null, false);

            case "STATUS" -> new Request(
                    CommandType.STATUS,
                    null, null, false);

            default -> new Request(
                    CommandType.UNKNOWN,
                    null, null, false);
        };
    }

    private static Request parseInternal(
            String line) {

        // Strip "INTERNAL " prefix
        String rest = line.substring(9).trim();
        String[] parts = rest.split(" ", 4);
        String sub = parts[0].toUpperCase();

        return switch (sub) {

            case "REPLICATE" -> {
                String op = parts[1].toUpperCase();
                yield switch (op) {
                    case "PUT" -> new Request(
                            CommandType.INTERNAL_REPLICATE_PUT,
                            parts[2],
                            parts.length > 3
                                    ? parts[3] : null,
                            true);
                    case "DELETE" -> new Request(
                            CommandType.INTERNAL_REPLICATE_DELETE,
                            parts[2],
                            null, true);
                    case "GET" -> new Request(
                            CommandType.INTERNAL_REPLICATE_GET,
                            parts[2],
                            null, true);
                    default -> new Request(
                            CommandType.UNKNOWN,
                            null, null, true);
                };
            }

            case "PING" -> new Request(
                    CommandType.INTERNAL_PING,
                    parts.length > 1
                            ? parts[1] : null,
                    null, true);

            case "PONG" -> new Request(
                    CommandType.INTERNAL_PONG,
                    parts.length > 1
                            ? parts[1] : null,
                    null, true);

            case "FORWARD" -> {
                String op = parts[1].toUpperCase();
                yield switch (op) {
                    case "PUT" -> new Request(
                            CommandType.INTERNAL_FORWARD_PUT,
                            parts[2],
                            parts.length > 3
                                    ? parts[3] : null,
                            true);
                    case "GET" -> new Request(
                            CommandType.INTERNAL_FORWARD_GET,
                            parts[2],
                            null, true);
                    default -> new Request(
                            CommandType.UNKNOWN,
                            null, null, true);
                };
            }

            case "HINT" -> {
                String op = parts[1].toUpperCase();
                yield switch (op) {
                    case "PUT" -> new Request(
                            CommandType.INTERNAL_HINT_PUT,
                            parts[2],
                            parts.length > 3
                                    ? parts[3] : null,
                            true);
                    case "FLUSH" -> new Request(
                            CommandType.INTERNAL_HINT_FLUSH,
                            parts.length > 1
                                    ? parts[1] : null,
                            null, true);
                    default -> new Request(
                            CommandType.UNKNOWN,
                            null, null, true);
                };
            }

            case "JOIN" -> new Request(
                    CommandType.INTERNAL_JOIN,
                    parts.length > 1
                            ? parts[1] : null,
                    parts.length > 2
                            ? parts[2] + ":"
                              + (parts.length > 3
                                 ? parts[3] : "") : null,
                    true);

            case "LEAVE" -> new Request(
                    CommandType.INTERNAL_LEAVE,
                    parts.length > 1
                            ? parts[1] : null,
                    null, true);

            case "STATUS" -> new Request(
                    CommandType.INTERNAL_STATUS,
                    null, null, true);

            default -> new Request(
                    CommandType.UNKNOWN,
                    null, null, true);
        };
    }
}
