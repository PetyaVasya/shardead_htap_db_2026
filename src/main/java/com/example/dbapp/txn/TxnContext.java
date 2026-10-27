package com.example.dbapp.txn;

import com.example.dbapp.common.TxnId;

public record TxnContext(TxnId id, IsolationLevel isolation) {
    public TxnContext {
        if (id == null || isolation == null) {
            throw new IllegalArgumentException("id and isolation are required");
        }
    }
}
