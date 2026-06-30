package ru.mcs.llmrobot.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Парсит tool_calls из ответа LLM в формате OpenAI.
 *
 * Поддерживает два формата:
 * 1. Нативный OpenAI: choices[0].message.tool_calls[]
 * 2. Fallback: ищет JSON-блок <tool_call>{...}</tool_call> в тексте
 */
public class ToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(ToolCallParser.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Парсит нативный ответ LLM (полный JSON-ответ от /chat/completions).
     */
    public List<ToolCallRequest> parseResponse(String jsonResponse) {
        var result = new ArrayList<ToolCallRequest>();
        try {
            var root    = mapper.readTree(jsonResponse);
            var message = root.path("choices").path(0).path("message");
            var toolCalls = message.path("tool_calls");

            if (toolCalls.isArray()) {
                for (var tc : toolCalls) {
                    var id   = tc.path("id").asText();
                    var name = tc.path("function").path("name").asText();
                    var argsStr = tc.path("function").path("arguments").asText();
                    Map<String, Object> args = parseArgs(argsStr);
                    result.add(new ToolCallRequest(id, name, args));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse tool_calls from response: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Парсит дельты из streaming-ответа (накопленный JSON tool_calls).
     */
    public List<ToolCallRequest> parseAccumulated(Map<String, ToolCallAccumulator> accumulated) {
        var result = new ArrayList<ToolCallRequest>();
        for (var entry : accumulated.entrySet()) {
            var acc = entry.getValue();
            if (acc.name != null && !acc.name.isBlank()) {
                result.add(new ToolCallRequest(
                        acc.id,
                        acc.name,
                        parseArgs(acc.arguments.toString())
                ));
            }
        }
        return result;
    }

    /**
     * Fallback: ищет <tool_call>{"name":..., "arguments":{...}}</tool_call> в тексте.
     * Используется для моделей, которые не поддерживают нативный tool_calling.
     */
    public List<ToolCallRequest> parseFromText(String text) {
        var result = new ArrayList<ToolCallRequest>();
        int start = 0;
        while (true) {
            int open  = text.indexOf("<tool_call>", start);
            int close = text.indexOf("</tool_call>", open + 1);
            if (open < 0 || close < 0) break;
            var json = text.substring(open + 11, close).trim();
            try {
                var node = mapper.readTree(json);
                var name = node.path("name").asText();
                var args = parseArgs(node.path("arguments").toString());
                result.add(new ToolCallRequest(UUID.randomUUID().toString(), name, args));
            } catch (Exception e) {
                log.warn("Failed to parse inline tool_call: {}", e.getMessage());
            }
            start = close + 12;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return Map.of();
        try {
            return mapper.readValue(argsStr, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool args: {}", argsStr);
            return Map.of();
        }
    }

    /** Аккумулятор для streaming tool_calls (дельты приходят по частям) */
    public static class ToolCallAccumulator {
        public String id;
        public String name;
        public StringBuilder arguments = new StringBuilder();
    }
}