package com.example.dbapp.storage;

public interface MemoryBounded {

    long bytesUsed();

    long bytesLimit();
}
