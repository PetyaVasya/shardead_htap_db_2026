package com.example.dbapp.storage.buffer;

import com.example.dbapp.storage.MemoryBounded;

public interface SharedBuffer extends BufferAppender, BufferDrain, BufferAbort, MemoryBounded {
}
