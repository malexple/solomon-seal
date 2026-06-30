package ru.mcs.llmrobot.core.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    public String role;
    public String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role    = role;
        this.content = content;
    }
}