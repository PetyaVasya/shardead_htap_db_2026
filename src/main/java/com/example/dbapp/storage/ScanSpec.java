package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.SortKey;
import com.example.dbapp.common.TableId;
import java.util.List;
import java.util.function.Predicate;

public record ScanSpec(
        TableId table,
        Predicate<Row> filter,
        List<SortKey> sort,
        List<String> projection
) {
    public ScanSpec {
        if (table == null) {
            throw new IllegalArgumentException("table is required");
        }
        filter = filter == null ? r -> true : filter;
        sort = sort == null ? List.of() : List.copyOf(sort);
        projection = projection == null ? List.of() : List.copyOf(projection);
    }

    public static ScanSpec of(TableId table) {
        return new ScanSpec(table, null, null, null);
    }
}
