package com.example.dbapp.storage.wal;

import com.example.dbapp.common.Mutation;
import com.example.dbapp.common.SegmentId;
import com.example.dbapp.common.TableId;
import com.example.dbapp.common.TxnId;
import com.example.dbapp.txn.TxnStatus;

public sealed interface WalRecord {

    record DataChange(Mutation mutation) implements WalRecord {
        public DataChange {
            if (mutation == null) {
                throw new IllegalArgumentException("mutation is required");
            }
        }
    }

    record TxnBoundary(TxnId xid, TxnStatus status) implements WalRecord {
        public TxnBoundary {
            if (xid == null || status == null) {
                throw new IllegalArgumentException("xid and status are required");
            }
            if (status == TxnStatus.PENDING) {
                throw new IllegalArgumentException("TxnBoundary status must be COMMITTED or ABORTED");
            }
        }
    }

    record SpillMark(TableId table, SegmentId segment) implements WalRecord {
        public SpillMark {
            if (table == null || segment == null) {
                throw new IllegalArgumentException("table and segment are required");
            }
        }
    }
}
