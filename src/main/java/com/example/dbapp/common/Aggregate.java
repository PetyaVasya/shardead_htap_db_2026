package com.example.dbapp.common;

public record Aggregate(AggregateFunction function, String column, String alias) {
    public Aggregate {
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
        if (function != AggregateFunction.COUNT && (column == null || column.isBlank())) {
            throw new IllegalArgumentException("column is required for " + function);
        }
    }

    public static Aggregate countStar(String alias) {
        return new Aggregate(AggregateFunction.COUNT, null, alias);
    }
}
