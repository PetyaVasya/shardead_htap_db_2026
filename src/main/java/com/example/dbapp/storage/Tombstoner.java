package com.example.dbapp.storage;

import com.example.dbapp.common.RowKey;
import com.example.dbapp.common.TableId;

public interface Tombstoner {

    void tombstone(TableId table, RowKey key);
}
