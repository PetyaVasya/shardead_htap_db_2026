package com.example.dbapp.query;

import com.example.dbapp.storage.RowCursor;

public interface QueryEngine {

    RowCursor execute(String sql);

    RowCursor run(PhysicalPlan plan);
}
