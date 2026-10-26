package com.example.dbapp.common;

import java.util.List;

public record Schema(List<Column> columns, int pkIndex) {
    public Schema {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("schema must have at least one column");
        }
        if (pkIndex < 0 || pkIndex >= columns.size()) {
            throw new IllegalArgumentException("pkIndex out of range");
        }
        columns = List.copyOf(columns);
    }

    public Column pk() {
        return columns.get(pkIndex);
    }
}
