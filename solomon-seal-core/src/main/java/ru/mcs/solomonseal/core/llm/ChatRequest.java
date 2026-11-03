package ru.mcs.solomonseal.core.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        boolean stream,
        @JsonProperty("max_tokens") Integer maxTokens,
        List<Map<String, Object>> tools
) {
    public ChatRequest(String model, List<ChatMessage> messages, boolean stream, Integer maxTokens) {
        this(model, messages, stream, maxTokens, null);
    }
}
