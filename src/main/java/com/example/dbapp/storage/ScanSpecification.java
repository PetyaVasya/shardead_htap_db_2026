package com.example.dbapp.storage;

import com.example.dbapp.common.Aggregate;
import com.example.dbapp.common.Row;
import com.example.dbapp.common.SortKey;
import com.example.dbapp.common.TableId;
import com.example.dbapp.txn.Snapshot;
import java.util.List;
import java.util.function.Predicate;

public record ScanSpecification(
        TableId table,
        Predicate<Row> filter,
        List<SortKey> sort,
        List<String> projection,
        List<String> groupBy,
        List<Aggregate> aggregates,
        Snapshot snapshot
) {
    public ScanSpecification {
        if (table == null) {
            throw new IllegalArgumentException("table is required");
        }
        filter = filter == null ? r -> true : filter;
        sort = sort == null ? List.of() : List.copyOf(sort);
        projection = projection == null ? List.of() : List.copyOf(projection);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
    }

    public static Builder builder(TableId table) {
        return new Builder(table);
    }

    public static final class Builder {
        private final TableId table;
        private Predicate<Row> filter;
        private List<SortKey> sort;
        private List<String> projection;
        private List<String> groupBy;
        private List<Aggregate> aggregates;
        private Snapshot snapshot;

        private Builder(TableId table) {
            this.table = table;
        }

        public Builder filter(Predicate<Row> filter)       { this.filter = filter; return this; }
        public Builder sort(List<SortKey> sort)             { this.sort = sort; return this; }
        public Builder projection(List<String> projection)  { this.projection = projection; return this; }
        public Builder groupBy(List<String> groupBy)        { this.groupBy = groupBy; return this; }
        public Builder aggregates(List<Aggregate> aggs)     { this.aggregates = aggs; return this; }
        public Builder snapshot(Snapshot snapshot)          { this.snapshot = snapshot; return this; }

        public ScanSpecification build() {
            return new ScanSpecification(table, filter, sort, projection, groupBy, aggregates, snapshot);
        }
    }
}
