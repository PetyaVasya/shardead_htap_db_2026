package com.example.dbapp.storage.rowstore;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.TableId;
import com.example.dbapp.storage.Catalog;
import com.example.dbapp.storage.RowCursor;
import com.example.dbapp.storage.TableStore;

public interface RowStore extends TableStore, Catalog {

    void insert(TableId table, Row row);

    void update(TableId table, Row row);

    void delete(TableId table, RowKey key);

    long bytesUsed();

    long bytesLimit();

    boolean hasCapacityFor(Row row);

    RowCursor drain(TableId table);
}
