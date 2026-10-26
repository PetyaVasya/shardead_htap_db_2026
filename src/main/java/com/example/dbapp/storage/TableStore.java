package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.TableId;
import java.util.Optional;

public interface TableStore {

    Optional<Row> get(TableId table, RowKey key);

    RowCursor scan(ScanSpec spec);

    JoinedRowCursor join(JoinSpec spec);
}
