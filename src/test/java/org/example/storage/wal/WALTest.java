import org.example.storage.wal.WAL;
import org.example.storage.wal.WALEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.example.storage.wal.WAL.recover;

public class WALTest {
    String key = "test_key_1";
    String val = "test_val_1";
    Byte operation = 0x001;
    long timestamp = 1;

    byte[] key_byte_arr = key.getBytes();
    byte[] val_byte_arr = val.getBytes();

    WALEntry entry = new WALEntry(key_byte_arr, val_byte_arr, operation, timestamp);

    @BeforeEach
    void setup() {
        new File("test_file.txt").delete();
    }

    @Test
    public void test() {
        try {
            WAL engine = new WAL("test_file.txt");
            engine.append(entry);
            engine.close();
            List<WALEntry> ans_list = new ArrayList<>();
            ans_list.add(entry);
            List<WALEntry> list = recover("test_file.txt");
            assertArrayEquals(ans_list.getFirst().key(), list.getFirst().key());
            assertArrayEquals(ans_list.getFirst().val(), list.getFirst().val());
            assertEquals(ans_list.getFirst().operation(), list.getFirst().operation());
            assertEquals(ans_list.getFirst().timestamp(), list.getFirst().timestamp());
//            System.out.println(list.getFirst());
        } catch (IOException e) {
            System.out.println("IOException ouccured");
        }
    }
}
