package com.example.dbapp.common;

public record Column(String name, ColumnType type) {
    public Column {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("column name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("column type is required");
        }
    }
}
