package com.dynamo.lite.protocol;

public class Response {

    public static String ok() {
        return "OK";
    }

    public static String value(String v) {
        return "VALUE " + v;
    }

    public static String notFound() {
        return "NOT_FOUND";
    }

    public static String pong() {
        return "PONG";
    }

    public static String ack() {
        return "ACK";
    }

    public static String error(String code) {
        return "ERROR " + code;
    }

    public static String status(String json) {
        return "STATUS " + json;
    }
}
