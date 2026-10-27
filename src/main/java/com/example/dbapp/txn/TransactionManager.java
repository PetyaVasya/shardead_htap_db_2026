package com.example.dbapp.txn;

public interface TransactionManager {

    TxnContext begin(IsolationLevel level);

    void commit(TxnContext txn);

    void abort(TxnContext txn);

    Snapshot snapshot(TxnContext txn);
}
