package com.example.dbapp.common;

public record Mutation(Op op, TableId table, RowKey key, Row row, TxnId xid) {
    public Mutation {
        if (op == null || table == null || key == null) {
            throw new IllegalArgumentException("op, table, key are required");
        }
        if (op != Op.DELETE && row == null) {
            throw new IllegalArgumentException("row is required for " + op);
        }
    }

    public static Mutation autoCommit(Op op, TableId table, RowKey key, Row row) {
        return new Mutation(op, table, key, row, null);
    }
}
