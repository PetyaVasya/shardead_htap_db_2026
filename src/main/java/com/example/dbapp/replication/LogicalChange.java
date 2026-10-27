package com.example.dbapp.replication;

import com.example.dbapp.common.Mutation;
import com.example.dbapp.common.TxnId;
import java.util.List;

public record LogicalChange(long lsn, TxnId xid, List<Mutation> mutations) {
    public LogicalChange {
        if (xid == null) {
            throw new IllegalArgumentException("xid is required");
        }
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
    }
}
