package com.example.dbapp.common;

public record TableId(String name) {
    public TableId {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("table name must not be blank");
        }
    }
}
