package org.example.storage.memtable;

import org.example.storage.wal.WALEntry;

import java.nio.ByteBuffer;

public interface Memtable {
    boolean put(ByteBuffer key, WALEntry val);

    WALEntry get(ByteBuffer key);

    boolean delete(ByteBuffer key);

    default boolean isClean() {
        return true;
    }

    Iterable<WALEntry> getFlushSet();

    interface Factory {
        Memtable create();
    }
}
