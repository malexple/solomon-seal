package ru.mcs.solomonseal.core.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResultMessage extends ChatMessage {

    @JsonProperty("tool_call_id")
    public String toolCallId;

    public String name;

    public ToolResultMessage() {}

    public ToolResultMessage(String toolCallId, String name, String content) {
        super("tool", content);
        this.toolCallId = toolCallId;
        this.name       = name;
    }
}