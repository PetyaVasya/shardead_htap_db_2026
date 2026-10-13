package db.htap.wal

import db.htap.model.Row
import db.htap.model.RowKey

sealed class WalRecord {
    abstract val xid: Long

    data class Begin(override val xid: Long) : WalRecord()
    data class Commit(override val xid: Long) : WalRecord()
    data class Abort(override val xid: Long) : WalRecord()

    sealed class Mutation : WalRecord() { abstract val table: String }
    data class Insert(override val xid: Long, override val table: String, val row: Row) : Mutation()
    data class Update(
        override val xid: Long,
        override val table: String,
        val key: RowKey,
        val row: Row,
    ) : Mutation()
    data class Delete(
        override val xid: Long,
        override val table: String,
        val key: RowKey,
    ) : Mutation()
}

interface Wal : AutoCloseable {
    fun append(record: WalRecord)
    fun flush()
    fun readAll(): Sequence<WalRecord>
}
