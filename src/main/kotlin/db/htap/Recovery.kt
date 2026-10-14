package db.htap

import db.htap.cache.MvccRowCache
import db.htap.txn.MvccTxnManager
import db.htap.wal.Wal
import db.htap.wal.WalRecord

object Recovery {
    /** Replay WAL into [cache] and prime [txnMgr]. Catalog must already have the schemas. */
    fun replay(wal: Wal, cache: MvccRowCache, txnMgr: MvccTxnManager) {
        val records = wal.readAll().toList()
        val committed = records.filterIsInstance<WalRecord.Commit>().mapTo(HashSet()) { it.xid }
        val maxXid = records.maxOfOrNull { it.xid } ?: 0L
        txnMgr.seedFromRecovery(maxXid, committed)
        records.asSequence()
            .filterIsInstance<WalRecord.Mutation>()
            .filter { it.xid in committed }
            .forEach(cache::applyRecoveredMutation)
    }
}
