package ru.mcs.llmrobot.core.config;

import ru.mcs.llmrobot.core.llm.ModelConfig;
import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public ModelConfig defaultModel = new ModelConfig();
    public List<ModelConfig> savedModels = new ArrayList<>();
    public String theme = "dark";

    public AppConfig() {
        // defaults — LiteLLM on localhost
        defaultModel.baseUrl = "http://localhost:4000/v1";
        defaultModel.apiKey = "sk-1234";
        defaultModel.modelId = "gpt-4o";
    }
}
