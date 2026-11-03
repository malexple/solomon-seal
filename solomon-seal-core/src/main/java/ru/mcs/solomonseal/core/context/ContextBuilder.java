package ru.mcs.solomonseal.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.mcs.solomonseal.core.llm.ChatMessage;
import ru.mcs.solomonseal.core.llm.ChatRequest;
import ru.mcs.solomonseal.core.llm.DebugPayload;
import ru.mcs.solomonseal.core.llm.RuntimeProfile;
import ru.mcs.solomonseal.core.session.Message;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.core.tokens.TokenEstimator;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Builds ChatRequest from Session + user query + RuntimeProfile.
 *
 * ВАЖНО: Qwen3.5-35B-A3B-FP8 (и ряд других моделей через vLLM) требует
 * чтобы system message был ровно один и только первым в списке.
 * Поэтому весь контекст (проект, файл, agentContext) конкатенируется
 * в единый system prompt — никаких дополнительных system messages.
 */
public class ContextBuilder {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful AI coding assistant. Be concise and precise.";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record BuildResult(ChatRequest request, DebugPayload debug) {}

    /**
     * Основной метод — без agentContext (ASK режим).
     */
    public BuildResult build(Session session, RuntimeProfile profile, String userInput) {
        return build(session, profile, userInput, null);
    }

    /**
     * Расширенный метод — с agentContext от оркестратора (PLAN/BUILD режим).
     * agentContext объединяется с system prompt, не добавляется отдельным сообщением.
     */
    public BuildResult build(Session session, RuntimeProfile profile, String userInput, String agentContext) {
        var messages = new ArrayList<ChatMessage>();

        // 1. Собираем единый system prompt — всё в одном сообщении
        var systemPrompt = new StringBuilder(DEFAULT_SYSTEM_PROMPT);

        switch (session.contextProfile) {
            case MINIMAL -> {
                // only history + usGjcvjneer message
            }
            case CURRENT_FILE -> {
                var fileContext = buildCurrentFileContext(session);
                if (fileContext != null) {
                    systemPrompt.append("\n\n").append(fileContext);
                }
            }
            case PROJECT_SUMMARY -> {
                var treeContext = buildProjectTreeContext(session);
                if (treeContext != null) {
                    systemPrompt.append("\n\n").append(treeContext);
                }
            }
            default -> {} // MINIMAL, FILE_SET, SELECTION
        }

        // Контекст от оркестратора дописываем в тот же system prompt
        if (agentContext != null && !agentContext.isBlank()) {
            systemPrompt.append("\n\n## Agent Context\n").append(agentContext);
        }

        // Единственный system message — всегда первый
        messages.add(new ChatMessage("system", systemPrompt.toString()));

        // 2. History (last 20 messages) — только user/assistant/tool, без system
        var history = session.history;
        var start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            var msg = history.get(i);
            // Пропускаем system сообщения из истории — они уже в system prompt выше
            if (msg.role == ru.mcs.solomonseal.core.session.Role.SYSTEM) continue;
            messages.add(toWire(msg));
        }

        // 3. Current user message
        messages.add(new ChatMessage("user", userInput));

        // 4. Build request
        var request = new ChatRequest(profile.modelId(), messages, true, 4096);

        // 5. Build debug payload
        int tokens = TokenEstimator.estimateMessages(messages);
        String rawJson;
        try {
            rawJson = mapper.writeValueAsString(request);
        } catch (Exception e) {
            rawJson = "{}";
        }
        var debug = new DebugPayload(
                profile.baseUrl() + "/chat/completions",
                profile.modelId(),
                messages, tokens, rawJson
        );

        return new BuildResult(request, debug);
    }

    private ChatMessage toWire(Message msg) {
        var role = switch (msg.role) {
            case USER      -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM    -> "system";
            case TOOL      -> "tool";
        };
        return new ChatMessage(role, msg.content);
    }

    private String buildCurrentFileContext(Session session) {
        if (session.projects.isEmpty()) return null;
        // TODO: Phase 3 — передавать текущий открытый файл из UI
        return null;
    }

    private String buildProjectTreeContext(Session session) {
        if (session.projects.isEmpty()) return null;
        var sb = new StringBuilder();
        sb.append("# Project structure\n\n");
        for (var project : session.projects) {
            sb.append("## ").append(project.displayName())
                    .append(" (").append(project.rootPath).append(")\n");
            if (project.description != null) sb.append(project.description).append("\n");
            appendTree(sb, project.root(), "", 0, 3);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, java.nio.file.Path dir, String prefix, int depth, int maxDepth) {
        if (depth >= maxDepth) return;
        try (var stream = Files.list(dir)) {
            var entries = stream
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> {
                        var n = p.getFileName().toString();
                        return !n.equals("node_modules") && !n.equals("build")
                                && !n.equals("target") && !n.equals(".gradle");
                    })
                    .sorted()
                    .toList();
            for (int i = 0; i < entries.size(); i++) {
                var entry   = entries.get(i);
                var last    = (i == entries.size() - 1);
                sb.append(prefix).append(last ? "└── " : "├── ")
                        .append(entry.getFileName()).append("\n");
                if (Files.isDirectory(entry)) {
                    appendTree(sb, entry, prefix + (last ? "    " : "│   "), depth + 1, maxDepth);
                }
            }
        } catch (IOException ignored) {}
    }
}