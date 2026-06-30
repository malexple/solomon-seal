package ru.mcs.llmrobot.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Хранит API-ключи отдельно от config.json.
 * Файл: ~/.llm-robot/secrets.json (chmod 600 на Unix)
 * Формат: { "profileId": "sk-xxx", ... }
 */
public class SecretsManager {

    private static final Logger log = LoggerFactory.getLogger(SecretsManager.class);

    private final Path secretsFile;
    private final ObjectMapper mapper;
    private Map<String, String> secrets;  // profileId → apiKey

    public SecretsManager() {
        this.secretsFile = Path.of(System.getProperty("user.home"), ".llm-robot", "secrets.json");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.secrets = load();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load() {
        try {
            if (Files.exists(secretsFile)) {
                return mapper.readValue(secretsFile.toFile(), HashMap.class);
            }
        } catch (IOException e) {
            log.warn("Could not load secrets: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    public String getApiKey(String profileId) {
        return secrets.getOrDefault(profileId, "");
    }

    public void setApiKey(String profileId, String apiKey) {
        secrets.put(profileId, apiKey);
        save();
    }

    public void removeApiKey(String profileId) {
        secrets.remove(profileId);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(secretsFile.getParent());
            mapper.writeValue(secretsFile.toFile(), secrets);
            // chmod 600 на Unix/Mac — игнорируем ошибку на Windows
            try {
                Files.setPosixFilePermissions(secretsFile, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                ));
            } catch (UnsupportedOperationException ignored) {
                // Windows — POSIX не поддерживается, файл просто сохраняется
            }
        } catch (IOException e) {
            log.error("Failed to save secrets: {}", e.getMessage());
        }
    }
}