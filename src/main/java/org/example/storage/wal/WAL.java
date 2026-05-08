package org.example.storage.wal;

import java.io.IOException;
import java.nio.channels.FileChannel; // java new I/O
import java.nio.file.StandardOpenOption;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WAL {
    public FileChannel channel;

    public WAL(String file_path) throws IOException {
        this.channel = FileChannel.open(Paths.get(file_path), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    public int append(WALEntry entry) throws IOException {

        int size = 1 + 4 + entry.key().length + 4 + entry.val().length + 8;
        // buffer = // 0x001(put, delete) + int(4 bytes) - store the length of the key + key +...
        // client(data) => temporariliy (buffer - temporary) => flushed -> wal(backup) -> memtable (RAM memory) => (sstable disk) (store in hashmap)

        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(entry.operation());
        buffer.putInt(entry.key().length);
        buffer.put(entry.key());
        buffer.putInt(entry.val().length);
        buffer.put(entry.val());
        buffer.putLong(entry.timestamp());

        buffer.flip();
        int bytes = channel.write(buffer);

        if (bytes < size) {
            throw new IOException("Incomplete WAL write: expected " + size + " bytes, wrote " + bytes);
        }

        return bytes;
    }

    public void close() throws IOException {
        channel.close();
    }

    public static List<WALEntry> recover(String file_path) throws IOException {
        FileChannel recoveryChannel = FileChannel.open(Paths.get(file_path), StandardOpenOption.READ);
        List<WALEntry> list = new ArrayList<>();

        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            if (recoveryChannel.read(buffer) == -1) {
                break;
            }
            buffer.flip();
            Byte operation = buffer.get();

            ByteBuffer keyLengthBuffer = ByteBuffer.allocate(4);
            recoveryChannel.read(keyLengthBuffer);
            keyLengthBuffer.flip();
            int keyLength = keyLengthBuffer.getInt();

            ByteBuffer keyValueBuffer = ByteBuffer.allocate(keyLength);
            recoveryChannel.read(keyValueBuffer);
            keyValueBuffer.flip();
            byte[] KeyValue = keyValueBuffer.array();

            ByteBuffer valLengthBuffer = ByteBuffer.allocate(4);
            recoveryChannel.read(valLengthBuffer);
            valLengthBuffer.flip();
            int valLength = valLengthBuffer.getInt();

            ByteBuffer valBuffer = ByteBuffer.allocate(valLength);
            recoveryChannel.read(valBuffer);
            valBuffer.flip();
            byte[] val = valBuffer.array();

            ByteBuffer timestampBuffer = ByteBuffer.allocate(8);
            recoveryChannel.read(timestampBuffer);
            timestampBuffer.flip();
            long timestamp = timestampBuffer.getLong();

            WALEntry entry = new WALEntry(KeyValue, val, operation, timestamp);
            list.add(entry);
        }
        recoveryChannel.close();
        return list;
    }
}
