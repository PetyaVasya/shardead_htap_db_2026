package com.example.dbapp.storage.buffer;

import com.example.dbapp.storage.MutationCursor;
import com.example.dbapp.txn.ClogReader;

public interface BufferDrain {

    MutationCursor drain(ClogReader clog);
}
