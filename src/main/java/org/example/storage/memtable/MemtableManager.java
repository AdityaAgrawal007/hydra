package org.example.storage.memtable;

/*
 * responsibilities of this class are:
 * maintain active memtable - done
 * switch memtables when current one becomes full - done
 * create new memtables using configured factory - done
 * pair memtables with allocators
 * trigger flush for immutable memtables - done
 */

import org.example.storage.flush.MemtableCleaner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class MemtableManager {
    public final Map<String, AtomicReference<Memtable>> registry;

    public MemtableManager(Map<String, AtomicReference<Memtable>> registry) {
        this.registry = registry;
    }

    // the memtable directly takes in the Factory (i.e. the object that knows how to create a certian type of memtable) and then just
    // runs the factory
    //    public Memtable createMemtable() {
    //        return factory.create();
    //    }
    private String getLargestMemtableKey() {
        String largestKey = null;
        long maxSize = 0;

        for (Map.Entry<String, AtomicReference<Memtable>> entry : registry.entrySet()) {
            long size = entry.getValue().get().size();

            if (size > maxSize) {
                maxSize = size;
                largestKey = entry.getKey();
            }
        }

        return largestKey;
    }

    boolean switchMemtable() throws Exception{
//        Memtable newMemtable = factory.create(); // 1.5 create a new active memtable

        String largestKey = getLargestMemtableKey();

        if (largestKey == null) {
            return false;
        }
        AtomicReference<Memtable> largestMemtable = registry.get(largestKey);
//        AtomicReference<Memtable> largestMemtable = null;
//        long maxSpace = 0;
//        for (Map.Entry<String, AtomicReference<Memtable>> entry : registry.entrySet()) {
//            AtomicReference<Memtable> currentMemtable = entry.getValue();
//            if (currentMemtable.get().size() >= maxSpace) {
//                largestMemtable = currentMemtable;
//                maxSpace = currentMemtable.get().size();
//            }
//        }

        String factoryClassName = "org.example.storage.memtable." + largestKey + "Factory";
        Class<?> clazz = Class.forName(factoryClassName);

        Memtable.Factory factory = (Memtable.Factory) clazz.getDeclaredConstructor().newInstance();

        Memtable newMemtable = factory.create();
        AtomicReference<Memtable> oldLargestMemtable = largestMemtable;
        largestMemtable.get().setImmutable(true); // 1. lock the memtable

        // 1.6 switch reference atomically
        assert largestMemtable != null;
        largestMemtable.getAndSet(newMemtable);

        // 2. trigger flush of the memtable async
        MemtableCleaner cleaner = new MemtableCleaner();
        Future<?> future = cleaner.flushLargestMemtable(oldLargestMemtable);
        future.get();

        // 3. discard the memtable
        oldLargestMemtable.get().discard();

        return true;
    }
}
