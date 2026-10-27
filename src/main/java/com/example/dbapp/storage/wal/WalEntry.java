package com.example.dbapp.storage.wal;

public record WalEntry(long lsn, WalRecord record) {
    public WalEntry {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
    }
}
