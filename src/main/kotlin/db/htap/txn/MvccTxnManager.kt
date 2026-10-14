package db.htap.txn

import db.htap.wal.Wal
import db.htap.wal.WalRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MvccTxnManager(private val wal: Wal) : TransactionManager {

    private val counter = AtomicLong(0)
    private val clog = ConcurrentHashMap<Long, XidStatus>()
    private val inProgress: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Synchronized
    override fun begin(isolation: IsolationLevel): TxnContext {
        val xid = counter.incrementAndGet()
        val active = HashSet(inProgress)
        inProgress.add(xid)
        clog[xid] = XidStatus.IN_PROGRESS
        wal.append(WalRecord.Begin(xid))
        return TxnContext(xid, Snapshot(xmax = xid + 1, inProgress = active), isolation)
    }

    override fun commit(txn: TxnContext) {
        if (txn.status != XidStatus.IN_PROGRESS) return
        wal.append(WalRecord.Commit(txn.xid))
        wal.flush()
        clog[txn.xid] = XidStatus.COMMITTED
        inProgress.remove(txn.xid)
        txn.status = XidStatus.COMMITTED
    }

    override fun abort(txn: TxnContext) {
        if (txn.status != XidStatus.IN_PROGRESS) return
        wal.append(WalRecord.Abort(txn.xid))
        clog[txn.xid] = XidStatus.ABORTED
        inProgress.remove(txn.xid)
        txn.status = XidStatus.ABORTED
    }

    override fun logMutation(txn: TxnContext, record: WalRecord.Mutation) {
        require(txn.status == XidStatus.IN_PROGRESS) { "txn ${txn.xid} not in progress" }
        require(txn.xid == record.xid) { "mutation xid ${record.xid} != txn ${txn.xid}" }
        wal.append(record)
    }

    override fun statusOf(xid: Long): XidStatus = clog[xid] ?: XidStatus.ABORTED

    override fun visibleTo(xid: Long, viewer: TxnContext): Boolean {
        if (xid == viewer.xid) return true
        if (xid >= viewer.snapshot.xmax) return false
        if (xid in viewer.snapshot.inProgress) return false
        return statusOf(xid) == XidStatus.COMMITTED
    }

    /** Called by Recovery after WAL replay to re-prime state. */
    internal fun seedFromRecovery(maxXid: Long, committed: Set<Long>) {
        counter.set(maxXid)
        committed.forEach { clog[it] = XidStatus.COMMITTED }
    }
}
