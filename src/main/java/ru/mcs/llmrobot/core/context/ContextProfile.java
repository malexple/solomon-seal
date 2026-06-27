package ru.mcs.llmrobot.core.context;

public enum ContextProfile {
    MINIMAL,        // system prompt + user message only
    SELECTION,      // + user-selected text
    CURRENT_FILE,   // + single file content
    FILE_SET,       // + multiple chosen files
    PROJECT_SUMMARY // + lightweight project tree
}
