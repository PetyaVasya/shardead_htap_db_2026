package com.example.dbapp.storage.wal;

public interface WalEntryCursor extends AutoCloseable {

    boolean next();

    WalEntry entry();

    @Override
    void close();
}
