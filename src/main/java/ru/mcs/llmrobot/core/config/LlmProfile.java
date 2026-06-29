package ru.mcs.llmrobot.core.config;

import java.util.UUID;

public class LlmProfile {
    public String id = UUID.randomUUID().toString();
    public String name = "New Profile";
    public String baseUrl = "http://localhost:11434/v1";
    public String apiKey = "ollama";
    public String modelId = "";
    public ProviderType type = ProviderType.OPENAI_COMPATIBLE;

    public LlmProfile() {}

    public LlmProfile(String name, String baseUrl, String apiKey, String modelId) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }
}