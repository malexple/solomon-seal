package ru.mcs.llmrobot.core.session;

public enum SessionMode {
    ASK,    // No tools, no agent loop — just Q&A
    PLAN,   // Read-only tools, exploration allowed
    BUILD   // Full tools, write/edit files
}
