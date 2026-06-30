package ru.mcs.llmrobot.core.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageWithToolCalls extends ChatMessage {

    @JsonProperty("tool_calls_raw")
    public String toolCallsRaw;

    public ChatMessageWithToolCalls() {}

    public ChatMessageWithToolCalls(String role, String content, String toolCallsRaw) {
        super(role, content);
        this.toolCallsRaw = toolCallsRaw;
    }
}