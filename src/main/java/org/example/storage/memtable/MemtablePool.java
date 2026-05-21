package org.example.storage.memtable;

import org.example.config.HydraConfig;
import org.example.storage.flush.MemtableCleaner;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MemtablePool {
    HydraConfig configObject;
    Map<String, Object> data;
    Integer memoryLimit;
    long currentBytes = 0;

    @SuppressWarnings("unchecked")
    public MemtablePool() {
        this.configObject = new HydraConfig();
        data = (Map<String, Object>) configObject.getData();
        if (data == null) {
            throw new IllegalStateException("memtable config missing");
        }
        memoryLimit = (Integer) data.get("memoryLimit");
    }

    boolean hasCapacity(long bytes) throws ExecutionException, InterruptedException {
        // how is using that 1224L ... different from just 10^9 ?
        return bytes + currentBytes <= memoryLimit * 1024L * 1024L * 1024L;
    }

    /*
    * seperation of concerns - the pools job is to only track the memory usage and trigger pressure actions it does
    * not have anything to do with registering and managing memtables hence we won't implement this here
    *     boolean register() {
    }
    */



}
