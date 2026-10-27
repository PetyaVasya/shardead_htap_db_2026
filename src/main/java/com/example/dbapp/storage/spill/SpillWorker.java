package com.example.dbapp.storage.spill;

import com.example.dbapp.common.TableId;

public interface SpillWorker {

    void spill(TableId table);

    void spillAll();
}
