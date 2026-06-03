package com.dynamo.lite.storage;

public class TestStorage {

    public static void main(String[] args) {

        StorageEngine s =
                new InMemoryStorageEngine();

        s.put("name", "alice");

        assert s.get("name")
                .get()
                .equals("alice")
                : "GET failed";

        s.put("name", "bob");

        assert s.get("name")
                .get()
                .equals("bob")
                : "Overwrite failed";

        assert s.delete("name")
                : "DELETE returned false";

        assert s.get("name").isEmpty()
                : "Key still present after delete";

        assert !s.delete("ghost")
                : "DELETE on missing key should be false";

        System.out.println(
                "StorageEngine: all tests passed");
    }
}
