package org.example.storage.flush;

import org.example.storage.memtable.Memtable;
import org.example.storage.wal.WALEntry;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/*
* convert the memtable into bytes
* write to the disk  */

public class MemtableCleaner {
    /**
     * Flushes the given memtable to disk as an SSTable.
     * Iterates over all entries in the memtable, serializes each one to bytes,
     * and writes them sequentially to disk.
     *
     * @param largestMemtable the memtable to flush, wrapped in an AtomicReference
     *                        to allow safe concurrent access during the flush lifecycle
     * @return a Future representing the async flush operation, null until async is implemented
     */
    public Future<?> flushLargestMemtable(AtomicReference<Memtable> largestMemtable ){
        Iterable<WALEntry> entries = largestMemtable.get().getFlushSet();
        MemtableSerializer serializer = new MemtableSerializer();
        entries.forEach(entry -> {serializer.serializer(entry);});
        return null;
    }
}
