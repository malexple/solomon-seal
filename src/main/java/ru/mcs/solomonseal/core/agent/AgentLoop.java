package ru.mcs.solomonseal.core.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mcs.solomonseal.core.config.OrchestratorProfile;
import ru.mcs.solomonseal.core.llm.ChatMessage;
import ru.mcs.solomonseal.core.llm.ChatRequest;
import ru.mcs.solomonseal.core.llm.LlmClient;
import ru.mcs.solomonseal.core.llm.RuntimeProfile;
import ru.mcs.solomonseal.core.tools.AgentTool;
import ru.mcs.solomonseal.core.tools.ExecutionContext;
import ru.mcs.solomonseal.core.tools.ToolRegistry;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Цикл агента:
 *   1. Отправить сообщения + tools в LLM
 *   2. Если ответ содержит tool_calls → выполнить инструменты
 *   3. Добавить результаты в историю
 *   4. Повторить до финального ответа или maxSteps
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final OrchestratorProfile config;
    private final LlmClient           llmClient;
    private final ToolRegistry        toolRegistry;
    private final ToolCallParser      parser = new ToolCallParser();
    private final ObjectMapper        mapper = new ObjectMapper();
    private final RuntimeProfile      runtimeProfile;

    public AgentLoop(OrchestratorProfile config, LlmClient llmClient, ToolRegistry toolRegistry) {
        this.config        = config;
        this.llmClient     = llmClient;
        this.toolRegistry  = toolRegistry;
        this.runtimeProfile = new RuntimeProfile(
                config.baseUrl, config.apiKey, config.modelId, config.trustSelfSignedCert
        );
    }

    /**
     * Запускает агентский цикл асинхронно.
     *
     * @param systemPrompt  системный промпт
     * @param userMessage   запрос пользователя
     * @param mode          режим сессии (определяет доступные инструменты)
     * @param ctx           контекст выполнения инструментов
     * @param onStep        колбэк прогресса — вызывается после каждого шага
     */
    public CompletableFuture<AgentResult> run(
            String systemPrompt,
            String userMessage,
            ru.mcs.solomonseal.core.session.SessionMode mode,
            ExecutionContext ctx,
            java.util.function.Consumer<String> onStep
    ) {
        return CompletableFuture.supplyAsync(() -> {
            var messages = new ArrayList<ChatMessage>();
            messages.add(new ChatMessage("system", systemPrompt));
            messages.add(new ChatMessage("user", userMessage));

            var toolLog   = new ArrayList<String>();
            var tools     = toolRegistry.forMode(mode);
            var toolDefs  = buildToolDefinitions(tools);

            for (int step = 0; step < config.maxSteps; step++) {
                log.debug("Agent step {}/{}", step + 1, config.maxSteps);

                // ① Запрос к LLM
                String responseJson;
                try {
                    responseJson = callLlm(messages, toolDefs);
                } catch (Exception e) {
                    return AgentResult.fail("LLM call failed: " + e.getMessage());
                }

                // ② Извлекаем tool_calls
                var toolCalls = parser.parseResponse(responseJson);

                if (toolCalls.isEmpty()) {
                    var content = extractContent(responseJson);
                    var textCalls = parser.parseFromText(content);
                    if (!textCalls.isEmpty()) {
                        toolCalls = textCalls;
                    }
                }

                // ③ Нет tool_calls → финальный ответ
                if (toolCalls.isEmpty()) {
                    var answer = extractContent(responseJson);
                    return AgentResult.ok(answer, toolLog);
                }

                // ④ Добавляем assistant-сообщение с tool_calls в историю
                messages.add(buildAssistantMessage(responseJson));

                // ⑤ Выполняем каждый tool_call
                for (var tc : toolCalls) {
                    var toolOpt = toolRegistry.get(tc.toolName());
                    if (toolOpt.isEmpty()) {
                        messages.add(toolResultMessage(tc.callId(), tc.toolName(),
                                "Unknown tool: " + tc.toolName()));
                        continue;
                    }

                    var tool    = toolOpt.get();
                    var logLine = "→ " + tc.toolName() + "(" + summarizeArgs(tc.args()) + ")";
                    log.info(logLine);
                    toolLog.add(logLine);
                    if (onStep != null) onStep.accept(logLine);

                    try {
                        var result = tool.execute(tc.args(), ctx).get();
                        var output = result.success()
                                ? (result.output() != null ? result.output() : "(ok)")
                                : "ERROR: " + result.error();
                        toolLog.add("← " + tc.toolName() + ": " + truncate(output, 200));
                        messages.add(toolResultMessage(tc.callId(), tc.toolName(), output));
                    } catch (Exception e) {
                        messages.add(toolResultMessage(tc.callId(), tc.toolName(),
                                "Execution error: " + e.getMessage()));
                    }
                }
            }

            return AgentResult.fail("Max steps (" + config.maxSteps + ") reached without final answer");
        });
    }

    // ── LLM call ─────────────────────────────────────────────────────────────

    private String callLlm(List<ChatMessage> messages, List<Map<String, Object>> tools) throws Exception {
        var request = new ChatRequest(
                config.modelId,
                messages,
                false,
                config.maxTokens,
                tools.isEmpty() ? null : tools
        );
        return llmClient.complete(runtimeProfile, request).get();
    }

    // ── Tool definitions (OpenAI format) ─────────────────────────────────────

    private List<Map<String, Object>> buildToolDefinitions(List<AgentTool> tools) {
        var result = new ArrayList<Map<String, Object>>();
        for (var tool : tools) {
            try {
                var schema = mapper.readValue(tool.parametersSchema(), Map.class);
                var fn = Map.of(
                        "name",        tool.name(),
                        "description", tool.description(),
                        "parameters",  schema
                );
                result.add(Map.of("type", "function", "function", fn));
            } catch (Exception e) {
                log.warn("Failed to build tool definition for {}: {}", tool.name(), e.getMessage());
            }
        }
        return result;
    }

    // ── Message helpers ───────────────────────────────────────────────────────

    private ChatMessage buildAssistantMessage(String responseJson) {
        try {
            var root      = mapper.readTree(responseJson);
            var msg       = root.path("choices").path(0).path("message");
            var content   = msg.path("content").isNull() ? null : msg.path("content").asText(null);
            var toolCallsNode = msg.path("tool_calls");
            List<Map<String, Object>> toolCalls = null;
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                toolCalls = mapper.convertValue(toolCallsNode, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
            return new ChatMessageWithToolCalls("assistant", content, toolCalls);
        } catch (Exception e) {
            return new ChatMessage("assistant", "");
        }
    }

    private ChatMessage toolResultMessage(String callId, String toolName, String content) {
        return new ToolResultMessage(callId, toolName, content);
    }

    private String extractContent(String responseJson) {
        try {
            var root = mapper.readTree(responseJson);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String summarizeArgs(Map<String, Object> args) {
        if (args.isEmpty()) return "";
        var first = args.entrySet().iterator().next();
        var val   = String.valueOf(first.getValue());
        return first.getKey() + "=" + truncate(val, 50);
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ── Inner message types для tool_calls ───────────────────────────────────

    /**
     * Сообщение ассистента с tool_calls в нативном OpenAI-формате.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatMessageWithToolCalls extends ChatMessage {
        @JsonProperty("tool_calls")
        public final List<Map<String, Object>> toolCalls;

        public ChatMessageWithToolCalls(String role, String content, List<Map<String, Object>> toolCalls) {
            super(role, content);
            this.toolCalls = toolCalls;
        }
    }

    /**
     * Результат выполнения инструмента в формате OpenAI.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolResultMessage extends ChatMessage {
        @JsonProperty("tool_call_id")
        public final String toolCallId;
        public final String name;

        public ToolResultMessage(String toolCallId, String name, String content) {
            super("tool", content);
            this.toolCallId = toolCallId;
            this.name       = name;
        }
    }
}