package com.example.dbapp.common;

public record Row(RowKey key, Object[] values) {
    public Row {
        if (key == null) {
            throw new IllegalArgumentException("row key is required");
        }
        if (values == null) {
            throw new IllegalArgumentException("row values are required");
        }
    }
}
