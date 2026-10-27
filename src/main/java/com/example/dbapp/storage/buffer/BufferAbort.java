package com.example.dbapp.storage.buffer;

import com.example.dbapp.common.TxnId;

public interface BufferAbort {

    void discard(TxnId xid);
}
