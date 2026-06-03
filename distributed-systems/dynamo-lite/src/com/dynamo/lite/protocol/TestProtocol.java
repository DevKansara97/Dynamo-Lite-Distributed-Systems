package com.dynamo.lite.protocol;

public class TestProtocol {

    public static void main(String[] args) {

        // PUT with multi-word value
        Request r1 = RequestParser.parse(
                "PUT greeting hello world");
        assert r1.getType()
                == CommandType.PUT;
        assert r1.getKey()
                .equals("greeting");
        assert r1.getValue()
                .equals("hello world")
                : "Multi-word value failed: "
                  + r1.getValue();

        // GET
        Request r2 = RequestParser.parse(
                "GET username");
        assert r2.getType()
                == CommandType.GET;
        assert r2.getKey()
                .equals("username");

        // DELETE
        Request r3 = RequestParser.parse(
                "DELETE username");
        assert r3.getType()
                == CommandType.DELETE;

        // INTERNAL REPLICATE PUT
        Request r4 = RequestParser.parse(
                "INTERNAL REPLICATE PUT"
                + " mykey myvalue");
        assert r4.getType()
                == CommandType.INTERNAL_REPLICATE_PUT;
        assert r4.isInternal();
        assert r4.getKey().equals("mykey");
        assert r4.getValue().equals("myvalue");

        // INTERNAL PING
        Request r5 = RequestParser.parse(
                "INTERNAL PING NodeA");
        assert r5.getType()
                == CommandType.INTERNAL_PING;
        assert r5.getKey().equals("NodeA");

        // PING (external)
        Request r6 = RequestParser.parse("PING");
        assert r6.getType()
                == CommandType.PING;

        // Unknown
        Request r7 = RequestParser.parse(
                "BLAH foo bar");
        assert r7.getType()
                == CommandType.UNKNOWN;

        System.out.println(
                "Protocol: all tests passed");
    }
}
