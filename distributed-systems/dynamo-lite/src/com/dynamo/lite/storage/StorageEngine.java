package com.dynamo.lite.storage;

import java.util.Optional;
import java.util.Set;

public interface StorageEngine {

    void put(String key, String value);

    Optional<String> get(String key);

    boolean delete(String key);

    int size();

    Set<String> keys();
}
