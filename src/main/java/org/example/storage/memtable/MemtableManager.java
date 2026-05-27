package org.example.storage.memtable;

/*
 * responsibilities of this class are:
 * maintain active memtable
 * switch memtables when current one becomes full
 * create new memtables using configured factory
 * pair memtables with allocators
 * trigger flush for immutable memtables
 */

import org.example.storage.flush.MemtableCleaner;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class MemtableManager {
    Memtable.Factory factory;
    Map<String, AtomicReference<Memtable>> registry;

    public MemtableManager(Memtable.Factory factory) {
        this.factory = factory;
    }

    // the memtable directly takes in the Factory (i.e. the object that knows how to create a certian type of memtable) and then just
    // runs the factory
    //    public Memtable createMemtable() {
    //        return factory.create();
    //    }

    boolean switchMemtable() throws ExecutionException, InterruptedException {
        Memtable newMemtable = factory.create(); // 1.5 create a new active memtable

        AtomicReference<Memtable> largestMemtable = null;
        long maxSpace = 0;
        for (Map.Entry<String, AtomicReference<Memtable>> entry : registry.entrySet()) {
            AtomicReference<Memtable> currentMemtable = entry.getValue();
            if (currentMemtable.get().size() >= maxSpace) {
                largestMemtable = currentMemtable;
                maxSpace = currentMemtable.get().size();
            }
        }

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
