package com.dynamo.lite.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashFunction {

    public static long hash(String input) {

        try {

            MessageDigest md =
                    MessageDigest.getInstance("MD5");

            byte[] digest =
                    md.digest(
                            input.getBytes(
                                    StandardCharsets.UTF_8));

            return ((digest[0] & 0xFFL) << 24)
                    | ((digest[1] & 0xFFL) << 16)
                    | ((digest[2] & 0xFFL) << 8)
                    | (digest[3] & 0xFFL);

        } catch (NoSuchAlgorithmException e) {

            throw new RuntimeException(
                    "MD5 not available",
                    e);
        }
    }
}

