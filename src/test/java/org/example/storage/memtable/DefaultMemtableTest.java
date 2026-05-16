package org.example.storage.memtable;

import org.example.storage.wal.WALEntry;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultMemtableTest {

    @Test
    public void test() {
        String key = "test_string01";
        String val = "test_value01";
        Byte operation = 0x01;
        long timestamp = 1;
        ByteBuffer buffer = ByteBuffer.allocate(key.length());
        buffer.put(key.getBytes());

        byte[] key_byte_arr = key.getBytes();
        byte[] val_byte_arr = val.getBytes();

        WALEntry entry = new WALEntry(key_byte_arr, val_byte_arr, operation, timestamp);

        DefaultMemtable testObject = new DefaultMemtable();

        testObject.put(buffer, entry);
        WALEntry returnedObj = testObject.get(buffer);

        assertArrayEquals(returnedObj.key(), key_byte_arr);
        assertArrayEquals(returnedObj.val(), val_byte_arr);
        assertEquals(operation, returnedObj.operation());
        assertEquals(timestamp, returnedObj.timestamp());

        testObject.delete(buffer);
        assertNull(testObject.get(buffer));
        testObject.delete(buffer);
    }
}

