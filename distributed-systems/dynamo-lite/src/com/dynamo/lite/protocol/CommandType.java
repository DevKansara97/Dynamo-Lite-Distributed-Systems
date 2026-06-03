package com.dynamo.lite.protocol;

public enum CommandType {

    // Client commands
    PUT,
    GET,
    DELETE,
    STATUS,
    PING,

    // Internal node-to-node
    INTERNAL_REPLICATE_PUT,
    INTERNAL_REPLICATE_DELETE,
    INTERNAL_REPLICATE_GET,
    INTERNAL_PING,
    INTERNAL_PONG,
    INTERNAL_FORWARD_PUT,
    INTERNAL_FORWARD_GET,
    INTERNAL_HINT_PUT,
    INTERNAL_HINT_FLUSH,
    INTERNAL_JOIN,
    INTERNAL_LEAVE,
    INTERNAL_STATUS,

    UNKNOWN
}
