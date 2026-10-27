package com.example.dbapp.storage;

import com.example.dbapp.common.TableId;

public interface RowDrain {

    RowCursor drain(TableId table);
}
