package com.example.dbapp.storage.wal;

public interface WalWriter {

    long append(WalRecord record);

    void fsync();
}
