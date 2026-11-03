package ru.mcs.solomonseal.core.config;

public enum ProviderType {
    OPENAI_COMPATIBLE,  // OpenAI, LiteLLM, Ollama /v1, Together, etc.
    OLLAMA_NATIVE,      // Ollama /api/chat — на будущее
    ANTHROPIC,          // на будущее
    GOOGLE              // на будущее
}