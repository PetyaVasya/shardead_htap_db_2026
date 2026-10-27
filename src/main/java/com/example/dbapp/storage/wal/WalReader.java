package com.example.dbapp.storage.wal;

public interface WalReader {

    WalEntryCursor replay();

    WalEntryCursor replayFrom(long fromLsn);
}
