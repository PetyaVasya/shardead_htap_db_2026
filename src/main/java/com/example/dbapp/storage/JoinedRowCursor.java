package com.example.dbapp.storage;

import com.example.dbapp.common.Row;

public interface JoinedRowCursor extends AutoCloseable {

    boolean next();

    Row left();

    Row right();

    @Override
    void close();
}
