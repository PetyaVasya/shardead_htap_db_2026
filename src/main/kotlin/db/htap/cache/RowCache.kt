package db.htap.cache

import db.htap.model.Row
import db.htap.model.RowKey
import db.htap.txn.TxnContext

interface RowCache {
    fun get(txn: TxnContext, table: String, key: RowKey): Row?
    fun insert(txn: TxnContext, table: String, row: Row)
    fun update(txn: TxnContext, table: String, key: RowKey, row: Row)
    fun delete(txn: TxnContext, table: String, key: RowKey)
    fun scan(
        txn: TxnContext,
        table: String,
        predicate: (Row) -> Boolean = { true },
    ): Sequence<Row>
}
