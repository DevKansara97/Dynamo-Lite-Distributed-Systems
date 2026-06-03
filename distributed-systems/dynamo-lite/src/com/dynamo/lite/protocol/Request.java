package com.dynamo.lite.protocol;

public class Request {

    private final CommandType type;
    private final String key;
    private final String value;
    private final boolean internal;

    public Request(
            CommandType type,
            String key,
            String value,
            boolean internal) {

        this.type = type;
        this.key = key;
        this.value = value;
        this.internal = internal;
    }

    public CommandType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isInternal() {
        return internal;
    }

    @Override
    public String toString() {
        return "Request{"
                + "type=" + type
                + ", key='" + key + '\''
                + ", value='" + value + '\''
                + ", internal=" + internal
                + '}';
    }
}
