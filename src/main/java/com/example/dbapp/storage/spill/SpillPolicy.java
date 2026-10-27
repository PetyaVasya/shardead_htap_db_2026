package com.example.dbapp.storage.spill;

import com.example.dbapp.storage.MemoryBounded;

public interface SpillPolicy {

    boolean shouldSpill(MemoryBounded source);
}
