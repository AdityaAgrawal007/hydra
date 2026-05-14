package org.example.storage.memtable;

public abstract class AbstractMemtable implements Memtable{
    protected long timestamp;
    protected long currentSize;
}
