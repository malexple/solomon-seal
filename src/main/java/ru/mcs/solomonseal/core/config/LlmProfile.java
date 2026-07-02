package ru.mcs.solomonseal.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Профиль подключения к LLM (без секретов — apiKey хранится в SecretsManager).
 */
public class LlmProfile {
    @JsonProperty
    public String id       = UUID.randomUUID().toString();
    @JsonProperty public String name     = "New Profile";
    @JsonProperty public String baseUrl  = "http://localhost:11434/v1";
    @JsonProperty public String modelId  = "";
    @JsonProperty public ProviderType type = ProviderType.OPENAI_COMPATIBLE;
    @JsonProperty public boolean trustSelfSignedCert = false;

    public LlmProfile() {}

    public LlmProfile(String name, String baseUrl, String modelId) {
        this.name    = name;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
    }

    @Override
    public String toString() {
        return name;  // для ComboBox
    }
}