package ru.mcs.llmrobot.core.agent;

import java.util.Map;

/**
 * Один tool_call из ответа LLM.
 */
public record ToolCallRequest(
        String callId,    // id из tool_calls[].id
        String toolName,  // tool_calls[].function.name
        Map<String, Object> args  // распарсенные аргументы
) {}