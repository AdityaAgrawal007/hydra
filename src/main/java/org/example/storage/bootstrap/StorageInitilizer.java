package org.example.storage.bootstrap;

import org.example.config.HydraConfig;
import org.example.storage.memtable.Memtable;
import org.example.storage.memtable.MemtableManager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

// 1. read the cofig
// 2. initilize all the different tables in the config
// 3. only one instance per table ? or multiple instances per table ?
public class StorageInitilizer {
    public static Map<String, AtomicReference<Memtable>> registry = new HashMap<>();

    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        HydraConfig config = new HydraConfig();
        Map<String, Object> data = config.getData();
        Map<String, Object> memtableConfigurations = (Map<String, Object>) data.get("memtable");
        Map<String, Object> configurations = (Map<String, Object>) memtableConfigurations.get("configurations");
        for (Map.Entry<String, Object> entry : configurations.entrySet()) {
            Map<String, Object> memtable = (Map<String, Object>) entry.getValue();
            String memtable_type = (String) memtable.get("class");
//            System.out.println(memtable_type);
            String factoryClassName = "org.example.storage.memtable." + memtable_type + "Factory";
            Class<?> clazz = Class.forName(factoryClassName);
            Memtable.Factory factory = (Memtable.Factory) clazz.getDeclaredConstructor().newInstance();
            registry.put(memtable_type, new AtomicReference<>(factory.create()));
        }
        MemtableManager manager = new MemtableManager(registry);
    }
}
