package com.example.dbapp.txn;

import com.example.dbapp.common.TxnId;

public interface ClogWriter {

    void mark(TxnId xid, TxnStatus status);
}
