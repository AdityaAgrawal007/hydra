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

    /*
    1. lock the memtable
    1.5 create a new active memtable
    1.6 switch reference atomically
    2. trigger flush of the memtable async
    3. discard the memtable
     */
    boolean switchMemtable(Memtable memtable) throws ExecutionException, InterruptedException {
        memtable.setImmutable(true);

        Memtable newMemtable = factory.create();




        MemtableCleaner cleaner = new MemtableCleaner();
        Future<?> future = cleaner.flushLargestMemtable();
        future.get();
    }


}
