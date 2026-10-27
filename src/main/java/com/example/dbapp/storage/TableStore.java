package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.TableId;
import com.example.dbapp.txn.Snapshot;
import java.util.Optional;

public interface TableStore {

    default Optional<Row> get(TableId table, RowKey key) {
        return get(table, key, null);
    }

    Optional<Row> get(TableId table, RowKey key, Snapshot snapshot);

    RowCursor scan(ScanSpecification spec);

    JoinedRowCursor join(JoinSpecification spec);
}
