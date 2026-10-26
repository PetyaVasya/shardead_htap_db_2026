package com.example.dbapp.storage;

import com.example.dbapp.common.Mutation;

public interface MutationCursor extends AutoCloseable {

    boolean next();

    Mutation mutation();

    @Override
    void close();
}
