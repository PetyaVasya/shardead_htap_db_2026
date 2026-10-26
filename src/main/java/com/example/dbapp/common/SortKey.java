package com.example.dbapp.common;

public record SortKey(String column, Direction direction) {
    public SortKey {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("column is required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
    }

    public static SortKey asc(String column) {
        return new SortKey(column, Direction.ASC);
    }

    public static SortKey desc(String column) {
        return new SortKey(column, Direction.DESC);
    }
}
