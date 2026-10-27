package com.example.dbapp.storage.colstore;

import com.example.dbapp.storage.Catalog;
import com.example.dbapp.storage.SegmentSink;
import com.example.dbapp.storage.Statistics;
import com.example.dbapp.storage.TableStore;

public interface ColumnStore extends TableStore, Catalog, Statistics, SegmentSink {
}
