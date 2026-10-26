package com.example.dbapp.storage;

import com.example.dbapp.common.Row;
import com.example.dbapp.common.SortKey;
import com.example.dbapp.common.TableId;
import java.util.List;
import java.util.function.Predicate;

public record JoinSpec(
        TableId left,
        TableId right,
        String joinColumn,
        Predicate<Row> filter,
        List<SortKey> sort
) {
    public JoinSpec {
        if (left == null || right == null) {
            throw new IllegalArgumentException("left and right tables are required");
        }
        if (joinColumn == null || joinColumn.isBlank()) {
            throw new IllegalArgumentException("joinColumn is required");
        }
        filter = filter == null ? r -> true : filter;
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public static JoinSpec of(TableId left, TableId right, String joinColumn) {
        return new JoinSpec(left, right, joinColumn, null, null);
    }
}
