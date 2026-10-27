package com.example.dbapp.storage.buffer;

import com.example.dbapp.common.Mutation;

public interface BufferAppender {

    void append(Mutation mutation);
}
