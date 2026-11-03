package ru.mcs.solomonseal.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private final Path configFile;
    private final ObjectMapper mapper;
    private AppConfig config;

    public ConfigManager() {
        this.configFile = Path.of(
            System.getProperty("user.home"), ".llm-robot", "config.json");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.config = load();
    }

    private AppConfig load() {
        try {
            if (Files.exists(configFile)) {
                return mapper.readValue(configFile.toFile(), AppConfig.class);
            }
        } catch (IOException e) {
            log.warn("Could not load config, using defaults: {}", e.getMessage());
        }
        return new AppConfig();
    }

    public AppConfig get() {
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            mapper.writeValue(configFile.toFile(), config);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage());
        }
    }
}
