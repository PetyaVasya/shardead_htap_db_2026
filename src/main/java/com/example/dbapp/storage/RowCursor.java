package com.example.dbapp.storage;

import com.example.dbapp.common.Row;

public interface RowCursor extends AutoCloseable {

    boolean next();

    Row row();

    @Override
    void close();
}
