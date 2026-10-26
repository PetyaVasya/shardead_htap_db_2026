package com.example.dbapp.storage;

import com.example.dbapp.common.Schema;
import com.example.dbapp.common.TableId;

public interface Catalog {

    void createTable(TableId table, Schema schema);

    void dropTable(TableId table);

    Schema schema(TableId table);

    Iterable<TableId> tables();

    long rowCount(TableId table);
}
