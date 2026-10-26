package com.example.dbapp.storage.wal;

import com.example.dbapp.common.Mutation;
import com.example.dbapp.storage.MutationCursor;

public interface Wal {

    void append(Mutation mutation);

    void fsync();

    MutationCursor replay();
}
