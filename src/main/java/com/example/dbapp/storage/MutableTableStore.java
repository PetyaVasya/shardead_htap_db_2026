package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.TableId;

public interface MutableTableStore extends TableStore {

    void insert(TableId table, Row row);

    void update(TableId table, Row row);

    void delete(TableId table, RowKey key);
}
