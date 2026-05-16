package org.example.storage.memtable;

import org.example.storage.wal.WALEntry;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentSkipListMap;

public class DefaultMemtable extends AbstractMemtable {

    /**
     * Arrays in Java use reference equality, not value equality.
     * <p>
     * Two byte arrays containing the same bytes are still treated
     * as different objects internally.
     * <p>
     * This breaks lookups and ordering inside maps because the map
     * compares object references instead of actual byte contents.
     * <p>
     * The wrapper fixes that by comparing arrays using their contents.but still instead of a seperate custom wrapper around
     * the array, we use ByteBuffer, but then again serialization doesn't happen right now so we use WALentry directly anyway
     */
    private final ConcurrentSkipListMap<ByteBuffer, WALEntry> entries;

    public DefaultMemtable() {
        entries = new ConcurrentSkipListMap<>();
    }

    @Override
    public boolean put(ByteBuffer key, WALEntry val) {
        // throwing exceptions for expected validation cases is expensive,
        // so we validate inputs explicitly
        if (key == null || val == null) {
            System.out.println("Key or Value cannot be Null");
            return false;
        }

        entries.put(key.asReadOnlyBuffer(), val);
        return true;
    }

    // we return the user the object in the type as it is and not the raw bytes hence we return WALentry
    @Override
    public WALEntry get(ByteBuffer key) {
        return entries.get(key);
    }

    @Override
    public boolean delete(ByteBuffer key) {
        entries.remove(key);
        return true;
    }

    @Override
    public Iterable<WALEntry> getFlushSet() {
        return entries.values();
    }

}