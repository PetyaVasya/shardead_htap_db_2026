package db.htap.cache

import db.htap.catalog.Catalog
import db.htap.model.Row
import db.htap.model.RowKey
import db.htap.txn.TransactionManager
import db.htap.txn.TxnContext
import db.htap.txn.WriteConflictException
import db.htap.txn.XidStatus
import db.htap.wal.WalRecord
import java.util.concurrent.ConcurrentHashMap

class MvccRowCache(
    private val catalog: Catalog,
    private val txnMgr: TransactionManager,
) : RowCache {

    internal class Version(
        val xmin: Long,
        @Volatile var xmax: Long,
        val tombstone: Boolean,
        val data: Row?,
        val prev: Version?,
    )

    internal class Chain { @Volatile var head: Version? = null }

    private val tables = ConcurrentHashMap<String, ConcurrentHashMap<RowKey, Chain>>()

    private fun tableOf(name: String): ConcurrentHashMap<RowKey, Chain> =
        tables.getOrPut(name) {
            require(catalog.exists(name)) { "table '$name' not in catalog" }
            ConcurrentHashMap()
        }

    override fun get(txn: TxnContext, table: String, key: RowKey): Row? {
        val chain = tables[table]?.get(key) ?: return null
        val v = visible(txn, chain) ?: return null
        return if (v.tombstone) null else v.data
    }

    override fun insert(txn: TxnContext, table: String, row: Row) {
        val key = row.keyFor(catalog.get(table))
        val chain = tableOf(table).getOrPut(key) { Chain() }
        synchronized(chain) {
            val vis = visible(txn, chain)
            if (vis != null && !vis.tombstone) {
                throw IllegalStateException("duplicate key in '$table': $key")
            }
            checkWriteConflict(txn, chain)
            chain.head?.let { if (it.xmax == 0L) it.xmax = txn.xid }
            chain.head = Version(txn.xid, 0L, tombstone = false, data = row, prev = chain.head)
        }
        txnMgr.logMutation(txn, WalRecord.Insert(txn.xid, table, row))
    }

    override fun update(txn: TxnContext, table: String, key: RowKey, row: Row) {
        val chain = tables[table]?.get(key)
            ?: throw IllegalStateException("no such row in '$table': $key")
        synchronized(chain) {
            val vis = visible(txn, chain)
                ?: throw IllegalStateException("no visible version to update: $key")
            if (vis.tombstone) throw IllegalStateException("row is deleted: $key")
            checkWriteConflict(txn, chain)
            chain.head!!.let { if (it.xmax == 0L) it.xmax = txn.xid }
            chain.head = Version(txn.xid, 0L, tombstone = false, data = row, prev = chain.head)
        }
        txnMgr.logMutation(txn, WalRecord.Update(txn.xid, table, key, row))
    }

    override fun delete(txn: TxnContext, table: String, key: RowKey) {
        val chain = tables[table]?.get(key)
            ?: throw IllegalStateException("no such row in '$table': $key")
        synchronized(chain) {
            val vis = visible(txn, chain)
                ?: throw IllegalStateException("no visible version to delete: $key")
            if (vis.tombstone) throw IllegalStateException("row already deleted: $key")
            checkWriteConflict(txn, chain)
            chain.head!!.let { if (it.xmax == 0L) it.xmax = txn.xid }
            chain.head = Version(txn.xid, 0L, tombstone = true, data = null, prev = chain.head)
        }
        txnMgr.logMutation(txn, WalRecord.Delete(txn.xid, table, key))
    }

    override fun scan(
        txn: TxnContext,
        table: String,
        predicate: (Row) -> Boolean,
    ): Sequence<Row> {
        val tbl = tables[table] ?: return emptySequence()
        return tbl.values.asSequence()
            .mapNotNull { chain -> visible(txn, chain)?.takeUnless { it.tombstone }?.data }
            .filter(predicate)
    }

    // --- MVCC visibility -----------------------------------------------------

    private fun visible(txn: TxnContext, chain: Chain): Version? {
        var v = chain.head
        while (v != null) {
            if (isVisible(txn, v)) return v
            v = v.prev
        }
        return null
    }

    private fun isVisible(txn: TxnContext, v: Version): Boolean {
        if (!txnMgr.visibleTo(v.xmin, txn)) return false
        val xmax = v.xmax
        if (xmax == 0L) return true
        if (xmax == txn.xid) return false  // own delete/update: this version is dead to me
        return !txnMgr.visibleTo(xmax, txn)
    }

    private fun checkWriteConflict(txn: TxnContext, chain: Chain) {
        val head = chain.head ?: return
        val xmax = head.xmax
        if (xmax != 0L && xmax != txn.xid) {
            when (txnMgr.statusOf(xmax)) {
                XidStatus.IN_PROGRESS ->
                    throw WriteConflictException("concurrent writer xid=$xmax on key")
                XidStatus.COMMITTED ->
                    if (!txnMgr.visibleTo(xmax, txn))
                        throw WriteConflictException("lost update: xid=$xmax committed after my snapshot")
                XidStatus.ABORTED -> {} // ignore
            }
        }
        if (xmax == 0L && head.xmin != txn.xid && !txnMgr.visibleTo(head.xmin, txn)) {
            throw WriteConflictException("newer version by xid=${head.xmin} not visible to me")
        }
    }

    // --- Recovery path (bypasses WAL) ---------------------------------------

    internal fun applyRecoveredMutation(r: WalRecord.Mutation) {
        val tbl = tables.getOrPut(r.table) {
            require(catalog.exists(r.table)) { "table '${r.table}' missing during recovery" }
            ConcurrentHashMap()
        }
        val (key, newHead) = when (r) {
            is WalRecord.Insert -> {
                val k = r.row.keyFor(catalog.get(r.table))
                k to { prev: Version? -> Version(r.xid, 0L, false, r.row, prev) }
            }
            is WalRecord.Update -> r.key to { prev: Version? -> Version(r.xid, 0L, false, r.row, prev) }
            is WalRecord.Delete -> r.key to { prev: Version? -> Version(r.xid, 0L, true, null, prev) }
        }
        val chain = tbl.getOrPut(key) { Chain() }
        chain.head?.let { if (it.xmax == 0L) it.xmax = r.xid }
        chain.head = newHead(chain.head)
    }
}
