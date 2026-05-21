package org.example.storage.wal;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    // Synchronous flush on every write. Durability over throughput, no buffering, no loss window on crash.
    public int append(WALEntry entry) throws IOException {
        int size = 1 + 4 + entry.key().length + 4 + entry.val().length + 8;


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

    /**
     * Opens a separate read-only channel because APPEND and READ are mutually exclusive in FileChannel - this
     * is an OS level constraint as every write automatically seeks to EOF and then there is no point reading from EOF.
     * On startup or crash recovery, replays all WAL entries in order to reconstruct memtable state.
     * Known gap: DELETE entries currently replay as memtable removals instead of tombstones. Without
     * tombstone replay, deleted keys can reappear from SSTables after recovery since compaction
     * never ran to physically remove them.
     */
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


    /**
     * Deletes all WAL entries before the given byte offset by copying everything from
     * byteOffset to EOF into a temp file, then replacing the original. FileChannel.truncate()
     * cannot remove from the beginning, only from the end, which is why a rewrite is necessary.
     */
    public static void truncateBefore(String file_path, long byteOffset) throws IOException {
        FileChannel truncationChannel = FileChannel.open(Paths.get(file_path), StandardOpenOption.READ);
        FileChannel temp = FileChannel.open(Paths.get(file_path + ".tmp"), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        truncationChannel.transferTo(byteOffset, Long.MAX_VALUE, temp);

        truncationChannel.close();
        temp.close();

        Files.move(Paths.get(file_path + ".tmp"), Paths.get(file_path), StandardCopyOption.REPLACE_EXISTING);
    }
    /**
     * TODO: WAL truncation - track last truncated byte offset in a checkpoint file (wal_checkpoint.meta).
     * Checkpoint must be written before truncation, not after, to survive mid-truncation crashes.
     * Each truncation operates from last checkpoint offset to new flush offset, not from 0.
     * Triggered by memtable after successful SSTable flush.
     */
}
