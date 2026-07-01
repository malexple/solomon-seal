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
     * Расширенный fallback — ищет JSON tool_call в нескольких форматах:
     * 1. <tool_call>{...}</tool_call>
     * 2. ```json\n{"name":..., "arguments":{...}}\n```
     * 3. {"name":..., "arguments":{...}} прямо в тексте
     */
    public List<ToolCallRequest> parseFromText(String text) {
        if (text == null || text.isBlank()) return List.of();
        var result = new ArrayList<ToolCallRequest>();

        // Формат 1: <tool_call>...</tool_call>
        result.addAll(extractByDelimiters(text, "<tool_call>", "</tool_call>"));

        // Формат 2: ```json ... ```
        result.addAll(extractByDelimiters(text, "```json\n", "\n```"));
        result.addAll(extractByDelimiters(text, "```\n", "\n```"));

        // Формат 3: голый JSON {"name": ..., "arguments": ...}
        if (result.isEmpty()) {
            result.addAll(extractBareJson(text));
        }

        return result;
    }

    private List<ToolCallRequest> extractByDelimiters(String text, String open, String close) {
        var result = new ArrayList<ToolCallRequest>();
        int start = 0;
        while (true) {
            int openIdx  = text.indexOf(open, start);
            if (openIdx < 0) break;
            int closeIdx = text.indexOf(close, openIdx + open.length());
            if (closeIdx < 0) break;
            var json = text.substring(openIdx + open.length(), closeIdx).trim();
            parseToolCallJson(json).ifPresent(result::add);
            start = closeIdx + close.length();
        }
        return result;
    }

    private List<ToolCallRequest> extractBareJson(String text) {
        var result = new ArrayList<ToolCallRequest>();
        // Ищем все JSON-объекты в тексте
        int i = 0;
        while (i < text.length()) {
            int braceOpen = text.indexOf('{', i);
            if (braceOpen < 0) break;
            // Находим matching закрывающую скобку
            int depth = 0;
            int braceClose = -1;
            for (int j = braceOpen; j < text.length(); j++) {
                if (text.charAt(j) == '{') depth++;
                else if (text.charAt(j) == '}') {
                    depth--;
                    if (depth == 0) { braceClose = j; break; }
                }
            }
            if (braceClose < 0) break;
            var json = text.substring(braceOpen, braceClose + 1);
            parseToolCallJson(json).ifPresent(result::add);
            i = braceClose + 1;
        }
        return result;
    }

    private Optional<ToolCallRequest> parseToolCallJson(String json) {
        try {
            var node = mapper.readTree(json);
            // Поддерживаем оба формата: {"name":..., "arguments":{}} и {"function":{"name":...}}
            String name = null;
            JsonNode argsNode = null;

            if (node.has("name")) {
                name     = node.get("name").asText();
                argsNode = node.path("arguments");
            } else if (node.has("function")) {
                name     = node.path("function").path("name").asText();
                argsNode = node.path("function").path("arguments");
            }

            if (name == null || name.isBlank()) return Optional.empty();

            Map<String, Object> args;
            if (argsNode == null || argsNode.isMissingNode()) {
                args = Map.of();
            } else if (argsNode.isTextual()) {
                args = parseArgs(argsNode.asText());
            } else {
                args = parseArgs(argsNode.toString());
            }

            return Optional.of(new ToolCallRequest(UUID.randomUUID().toString(), name, args));
        } catch (Exception e) {
            return Optional.empty();
        }
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