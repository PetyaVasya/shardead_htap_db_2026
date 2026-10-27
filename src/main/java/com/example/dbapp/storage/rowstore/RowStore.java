package com.example.dbapp.storage.rowstore;

import com.example.dbapp.storage.Catalog;
import com.example.dbapp.storage.MemoryBounded;
import com.example.dbapp.storage.MutableTableStore;
import com.example.dbapp.storage.RowDrain;
import com.example.dbapp.storage.Statistics;

public interface RowStore extends MutableTableStore, Catalog, Statistics, MemoryBounded, RowDrain {
}
