package com.example.dbapp.common;

public record Mutation(Op op, TableId table, RowKey key, Row row) {
    public Mutation {
        if (op == null || table == null || key == null) {
            throw new IllegalArgumentException("op, table, key are required");
        }
        if (op != Op.DELETE && row == null) {
            throw new IllegalArgumentException("row is required for " + op);
        }
    }
}
