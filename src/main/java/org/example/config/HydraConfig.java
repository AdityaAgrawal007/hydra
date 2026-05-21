package org.example.config;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import java.io.InputStream;

public class HydraConfig {

    private final Map<String, Object> data;

    public HydraConfig() {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("hydra.yaml");
        if (inputStream == null) throw new IllegalStateException("hydra.yaml not found on classpath");
        Yaml yaml = new Yaml();
        this.data = yaml.load(inputStream);
    }

    public Map<String, Object> getData(){
        return data;
    }
}
