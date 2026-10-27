package com.example.dbapp.query.operator;

import com.example.dbapp.storage.RowCursor;

public interface Operator extends RowCursor {

    void open();
}
