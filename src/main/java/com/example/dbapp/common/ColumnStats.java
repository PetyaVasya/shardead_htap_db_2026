package com.example.dbapp.common;

public record ColumnStats(
        Object min,
        Object max,
        long distinctCount,
        double nullFraction
) {
    public ColumnStats {
        if (distinctCount < 0) {
            throw new IllegalArgumentException("distinctCount must be >= 0");
        }
        if (nullFraction < 0.0 || nullFraction > 1.0) {
            throw new IllegalArgumentException("nullFraction must be in [0, 1]");
        }
    }
}
