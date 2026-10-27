package com.example.dbapp.txn;

import com.example.dbapp.common.TxnId;

public interface ClogReader {

    TxnStatus status(TxnId xid);
}
