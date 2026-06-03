package com.dynamo.lite.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DynamoLogger {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss");

    private final String nodeId;

    public DynamoLogger(String nodeId) {
        this.nodeId = nodeId;
    }

    private void log(
            String level, String message) {

        System.out.printf(
                "[%s] [%s] [%s] [%s] %s%n",
                LocalDateTime.now().format(FMT),
                nodeId,
                Thread.currentThread().getName(),
                level,
                message);
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }
}
