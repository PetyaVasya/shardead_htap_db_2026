package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.SortKey;
import com.example.dbapp.common.TableId;
import com.example.dbapp.txn.Snapshot;
import java.util.List;
import java.util.function.Predicate;

public record JoinSpecification(
        TableId left,
        TableId right,
        String joinColumn,
        Predicate<Row> filter,
        List<SortKey> sort,
        Snapshot snapshot
) {
    public JoinSpecification {
        if (left == null || right == null) {
            throw new IllegalArgumentException("left and right tables are required");
        }
        if (joinColumn == null || joinColumn.isBlank()) {
            throw new IllegalArgumentException("joinColumn is required");
        }
        filter = filter == null ? r -> true : filter;
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public static Builder builder(TableId left, TableId right, String joinColumn) {
        return new Builder(left, right, joinColumn);
    }

    public static final class Builder {
        private final TableId left;
        private final TableId right;
        private final String joinColumn;
        private Predicate<Row> filter;
        private List<SortKey> sort;
        private Snapshot snapshot;

        private Builder(TableId left, TableId right, String joinColumn) {
            this.left = left;
            this.right = right;
            this.joinColumn = joinColumn;
        }

        public Builder filter(Predicate<Row> filter) { this.filter = filter; return this; }
        public Builder sort(List<SortKey> sort)       { this.sort = sort; return this; }
        public Builder snapshot(Snapshot snapshot)    { this.snapshot = snapshot; return this; }

        public JoinSpecification build() {
            return new JoinSpecification(left, right, joinColumn, filter, sort, snapshot);
        }
    }
}
