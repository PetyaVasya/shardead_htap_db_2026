package com.example.dbapp.common;

public record RowKey(Object value) {
    public RowKey {
        if (value == null) {
            throw new IllegalArgumentException("row key must not be null");
        }
    }
}
