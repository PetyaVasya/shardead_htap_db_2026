package com.example.dbapp.storage;

import com.example.dbapp.common.SegmentId;
import com.example.dbapp.common.TableId;

public interface SegmentWriter {

    SegmentId appendSegment(TableId table, RowCursor rows);
}
