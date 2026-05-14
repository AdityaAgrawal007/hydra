package org.example.storage.memtable;

import org.example.storage.wal.WALEntry;

public interface Memtable {
    boolean put(byte[] key, byte[] val);
    byte[] get(byte[] key);
    boolean delete(byte[] key);
    default boolean isClean(){
        return true;
    }
    Iterable<WALEntry> getFlushSet();
    interface Factory{
        Memtable create();
    }
}
