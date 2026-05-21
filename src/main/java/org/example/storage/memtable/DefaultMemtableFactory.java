package org.example.storage.memtable;

public class DefaultMemtableFactory implements Memtable.Factory{
    @Override
    public Memtable create(){
        return new DefaultMemtable();
    }
}
