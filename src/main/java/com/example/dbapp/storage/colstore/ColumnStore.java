package com.example.dbapp.storage.colstore;

import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.SegmentId;
import com.example.dbapp.common.TableId;
import com.example.dbapp.storage.Catalog;
import com.example.dbapp.storage.RowCursor;
import com.example.dbapp.storage.TableStore;
import java.util.List;

public interface ColumnStore extends TableStore, Catalog {

    void appendSegment(TableId table, RowCursor rows);

    List<SegmentId> segments(TableId table);

    void tombstone(TableId table, RowKey key);
}
