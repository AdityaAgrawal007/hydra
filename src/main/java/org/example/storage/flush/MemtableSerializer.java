package org.example.storage.flush;

import org.example.storage.wal.WALEntry;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class MemtableSerializer {
    public ByteBuffer serializer(WALEntry entry){
        int size = entry.key().length + entry.val().length + 9;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        // 1. store the WAL entry in the order -> key length + key + val length + val + operation + timestamp
        buffer.put((byte) entry.key().length);
        buffer.put(entry.key());
        buffer.put((byte) entry.val().length);
        buffer.put(entry.val());
        buffer.put(entry.operation());
        buffer.put((byte) entry.timestamp());
        return buffer;
    }
}