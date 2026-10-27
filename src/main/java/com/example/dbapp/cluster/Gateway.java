package com.example.dbapp.cluster;

import com.example.dbapp.storage.RowCursor;

public interface Gateway {

    RowCursor execute(String sql);
}
