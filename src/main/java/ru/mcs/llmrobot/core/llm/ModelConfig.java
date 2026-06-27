package ru.mcs.llmrobot.core.llm;

public class ModelConfig {
    public String baseUrl = "http://localhost:4000/v1";
    public String apiKey = "";
    public String modelId = "gpt-4o";

    public ModelConfig() {}

    public ModelConfig(String baseUrl, String apiKey, String modelId) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }
}
