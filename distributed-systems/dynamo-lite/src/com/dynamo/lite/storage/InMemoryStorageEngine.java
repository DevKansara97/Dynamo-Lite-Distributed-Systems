package com.dynamo.lite.storage;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorageEngine
        implements StorageEngine {

    private final ConcurrentHashMap<String, String>
            store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        store.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public Set<String> keys() {
        return store.keySet();
    }
}
