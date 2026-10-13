package db.htap.txn

import db.htap.wal.WalRecord

enum class IsolationLevel { REPEATABLE_READ }

enum class XidStatus { IN_PROGRESS, COMMITTED, ABORTED }

data class Snapshot(
    /** First xid that was NOT yet assigned when this snapshot was taken. */
    val xmax: Long,
    /** Xids that were in-progress at snapshot time (excluding the owning txn). */
    val inProgress: Set<Long>,
)

class TxnContext internal constructor(
    val xid: Long,
    val snapshot: Snapshot,
    val isolation: IsolationLevel,
) {
    @Volatile internal var status: XidStatus = XidStatus.IN_PROGRESS
    val currentStatus: XidStatus get() = status
}

interface TransactionManager {
    fun begin(isolation: IsolationLevel = IsolationLevel.REPEATABLE_READ): TxnContext
    fun commit(txn: TxnContext)
    fun abort(txn: TxnContext)

    /** Called by StorageManager/Cache: TM is the sole writer to WAL. */
    fun logMutation(txn: TxnContext, record: WalRecord.Mutation)

    /** Status of an xid; unknown xids are treated as ABORTED. */
    fun statusOf(xid: Long): XidStatus

    /** Standard SI visibility: `xid` is treated as "committed as of viewer.snapshot". */
    fun visibleTo(xid: Long, viewer: TxnContext): Boolean
}

class WriteConflictException(message: String) : RuntimeException(message)
