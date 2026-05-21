package org.example.storage.wal;

public record WALEntry(byte[] key, byte[] val, Byte operation, long timestamp) {
    public static final Byte PUT = 0x01; // a hexadecimal needs 1 byte of space
    public static final Byte DELETE = 0x02;
}
