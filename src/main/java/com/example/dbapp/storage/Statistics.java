package com.example.dbapp.storage;

import com.example.dbapp.common.ColumnStats;
import com.example.dbapp.common.TableId;

public interface Statistics {

    long estimateRowCount(TableId table);

    ColumnStats columnStats(TableId table, String column);
}
