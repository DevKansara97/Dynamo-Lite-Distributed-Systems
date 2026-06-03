package com.dynamo.lite.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorageEngine {

    private final Map<String, String> storage;

    public InMemoryStorageEngine() {
        storage = new ConcurrentHashMap<>();
    }

    public String put(String key, String value) {
        storage.put(key, value);
        return "OK";
    }

    public String get(String key) {
        if (storage.containsKey(key)) {
            return "VALUE " + storage.get(key);
        }

        return "NOT_FOUND";
    }

    public String delete(String key) {
        if (storage.containsKey(key)) {
            storage.remove(key);
            return "OK";
        }

        return "NOT_FOUND";
    }

    public int size() {
        return storage.size();
    }
}

