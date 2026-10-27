package com.example.dbapp.txn;

import com.example.dbapp.common.TxnId;

public interface Snapshot {

    boolean isVisible(TxnId xid);
}
