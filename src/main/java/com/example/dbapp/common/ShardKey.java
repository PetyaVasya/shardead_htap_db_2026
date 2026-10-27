package com.example.dbapp.common;

public record ShardKey(Object value) {
    public ShardKey {
        if (value == null) {
            throw new IllegalArgumentException("shard key must not be null");
        }
    }
}
