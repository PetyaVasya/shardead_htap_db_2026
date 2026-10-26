package com.example.dbapp.storage.buffer;

import com.example.dbapp.common.Mutation;
import com.example.dbapp.storage.MutationCursor;

public interface SharedBuffer {

    void append(Mutation mutation);

    long bytesUsed();

    long bytesLimit();

    boolean hasCapacityFor(Mutation mutation);

    MutationCursor drain();
}
