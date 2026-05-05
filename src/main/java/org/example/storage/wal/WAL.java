package org.example.storage.wal;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel; // java new I/O
import java.nio.file.StandardOpenOption;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WAL {
    public FileChannel channel; // a high-performance, bidirectional channel used for reading, writing, mapping, and manipulating files

    WAL(String file_path) throws IOException {
        this.channel = FileChannel.open(Paths.get(file_path), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    public int append(WALEntry entry) throws IOException{

        byte[] keyBytes = entry.key().getBytes();
        byte[] valueBytes = entry.val().getBytes();

        int size = 1 + 4 + keyBytes.length + 4 + valueBytes.length + 8; // 1 byte for operation, 4 bytes to store an int which stores the length of the keyValue
        // same for valueBytes + 8 bytes for timestamp = no. of bytes we need

        // concatenate all vals of entry and then pass as one string below ?
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(entry.operation());
        buffer.putInt(keyBytes.length);
        buffer.put(keyBytes);
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        buffer.putLong(entry.timestamp());

        buffer.flip(); // sets the position of buffer back to 0 from end
        int bytes = channel.write(buffer);

        if(bytes < size){
            throw new IOException("Incomplete WAL write: expected " + size + " bytes, wrote " + bytes);
        }

        return bytes;
    }

    public void close() throws IOException{
        channel.close();
    }

    public static List<WALEntry> recover(String file_path) throws IOException{ // the method is declared static cause it does not use the class member
        // APPEND & READ are mutually exclusive options. APPEND every write automatically jumps to eof, then reading is not possible hence restricted by OS
        FileChannel recoveryChannel = FileChannel.open(Paths.get(file_path), StandardOpenOption.READ);
        List<WALEntry> list = new ArrayList<>(); // list is a varaible that can hold any implementation of the List interface, in this case  we choose ArrayList() as that interface
        // if later we want to use any other implementation we can only swap it here and rest will be taken care of cause all methods called on ArrayList will be availble for that other
        // implementation as well cause both have agreed on the List<> contract

        while(true){
            ByteBuffer buffer = ByteBuffer.allocate(1);
            if(recoveryChannel.read(buffer) == -1){
                break;
            } // here we perform a write operation on the buffer as the read() writes into that buffer
            buffer.flip(); // now because of the previous write operation the pointer now points to the end of the buffer hence we perform
            // flip to reset it back to 0 so that we can read from the buffer below
            Byte operation = buffer.get();

            ByteBuffer keyLengthBuffer = ByteBuffer.allocate(4);
            recoveryChannel.read(keyLengthBuffer);
            keyLengthBuffer.flip();
            int keyLength = keyLengthBuffer.getInt();

            ByteBuffer keyValueBuffer = ByteBuffer.allocate(keyLength);
            recoveryChannel.read(keyValueBuffer);
            keyValueBuffer.flip();
            String KeyValue = new String(keyValueBuffer.array());

            ByteBuffer valLengthBuffer = ByteBuffer.allocate(4);
            recoveryChannel.read(valLengthBuffer);
            valLengthBuffer.flip();
            int valLength = valLengthBuffer.getInt();

            ByteBuffer valBuffer = ByteBuffer.allocate(valLength);
            recoveryChannel.read(valBuffer);
            valBuffer.flip();
            String val = new String(valBuffer.array());

            ByteBuffer timestampBuffer = ByteBuffer.allocate(8);
            recoveryChannel.read(timestampBuffer);
            timestampBuffer.flip();
            long timestamp = timestampBuffer.getLong();

            WALEntry entry = new WALEntry(KeyValue, val, operation, timestamp);
            list.add(entry);
        }
        recoveryChannel.close(); // if exception is thrown mid loop recoverChannel colsing would remain that in future needs to
        // be handled via custom try catch block
        return list;
    }


}
