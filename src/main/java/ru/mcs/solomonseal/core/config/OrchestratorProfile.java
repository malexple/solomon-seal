package ru.mcs.solomonseal.core.config;

/**
 * Отдельный профиль для локальной LLM-оркестратора.
 * Хранится в AppConfig.orchestrator, не смешивается с основными профилями.
 */
public class OrchestratorProfile {
    public boolean enabled   = false;
    public String  baseUrl   = "http://localhost:11434/v1";  // Ollama по умолчанию
    public String  apiKey    = "ollama";
    public String  modelId   = "qwen2.5-coder:7b";
    public int     maxTokens = 4096;
    public int     maxSteps  = 10;   // макс итераций AgentLoop
    public boolean trustSelfSignedCert = false;

    public OrchestratorProfile() {}
}