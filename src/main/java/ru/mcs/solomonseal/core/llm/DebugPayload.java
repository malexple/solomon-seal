package ru.mcs.solomonseal.core.llm;

import java.util.List;

/**
 * Full debug snapshot of what will be sent to the LLM.
 * Shown in debug/payload panel in UI.
 */
public record DebugPayload(
        String endpoint,
        String modelId,
        List<ChatMessage> messages,
        int estimatedTokens,
        String rawJsonRequest
) {}
