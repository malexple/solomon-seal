package ru.mcs.llmrobot.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.mcs.llmrobot.core.config.LlmProfile;
import ru.mcs.llmrobot.core.llm.ChatMessage;
import ru.mcs.llmrobot.core.llm.ChatRequest;
import ru.mcs.llmrobot.core.llm.DebugPayload;
import ru.mcs.llmrobot.core.session.Message;
import ru.mcs.llmrobot.core.session.Role;
import ru.mcs.llmrobot.core.session.Session;
import ru.mcs.llmrobot.core.tokens.TokenEstimator;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds ChatRequest from Session + user query + context profile.
 * This is the heart of "what gets sent to the model".
 */
public class ContextBuilder {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful AI coding assistant. Be concise and precise.";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record BuildResult(ChatRequest request, DebugPayload debug) {}

    public BuildResult build(Session session, LlmProfile profile, String userInput) {
        var messages = new ArrayList<ChatMessage>();

        // 1. System prompt
        messages.add(new ChatMessage("system", DEFAULT_SYSTEM_PROMPT));

        // 2. Context by profile
        switch (session.contextProfile) {
            case MINIMAL -> {
                // only history + user message
            }
            case CURRENT_FILE -> {
                var fileContext = buildCurrentFileContext(session);
                if (fileContext != null) {
                    messages.add(new ChatMessage("system", fileContext));
                }
            }
            case PROJECT_SUMMARY -> {
                var treeContext = buildProjectTreeContext(session);
                if (treeContext != null) {
                    messages.add(new ChatMessage("system", treeContext));
                }
            }
            default -> {} // FILE_SET, SELECTION handled externally via addContext()
        }

        // 3. History (last N messages to avoid context overflow)
        int maxHistory = 20;
        var history = session.history;
        var start = Math.max(0, history.size() - maxHistory);
        for (int i = start; i < history.size(); i++) {
            var msg = history.get(i);
            messages.add(toWire(msg));
        }

        // 4. Current user message
        messages.add(new ChatMessage("user", userInput));

        // 5. Build request
        var request = new ChatRequest(profile.modelId, messages, true, 4096);


        // 6. Build debug payload
        int tokens = TokenEstimator.estimateMessages(messages);
        String rawJson;
        try {
            rawJson = mapper.writeValueAsString(request);
        } catch (Exception e) {
            rawJson = "{}";
        }
        var debug = new DebugPayload(
                profile.baseUrl + "/chat/completions",
                profile.modelId,
                messages, tokens, rawJson
        );

        return new BuildResult(request, debug);
    }

    private ChatMessage toWire(Message msg) {
        var role = switch (msg.role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
        return new ChatMessage(role, msg.content);
    }

    private String buildCurrentFileContext(Session session) {
        if (session.projects.isEmpty()) return null;
        // Placeholder — in real use, pass current file path from UI
        return null;
    }

    private String buildProjectTreeContext(Session session) {
        if (session.projects.isEmpty()) return null;
        var sb = new StringBuilder();
        sb.append("# Project structure\n\n");
        for (var project : session.projects) {
            sb.append("## ").append(project.alias).append(" (").append(project.root).append(")\n");
            if (project.description != null) sb.append(project.description).append("\n");
            appendTree(sb, project.root, "", 0, 3);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, java.nio.file.Path dir, String prefix, int depth, int maxDepth) {
        if (depth >= maxDepth) return;
        try (var stream = Files.list(dir)) {
            var entries = stream
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !p.getFileName().toString().equals("node_modules"))
                    .filter(p -> !p.getFileName().toString().equals("build"))
                    .filter(p -> !p.getFileName().toString().equals("target"))
                    .sorted()
                    .toList();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var last = (i == entries.size() - 1);
                sb.append(prefix).append(last ? "└── " : "├── ")
                  .append(entry.getFileName()).append("\n");
                if (Files.isDirectory(entry)) {
                    appendTree(sb, entry, prefix + (last ? "    " : "│   "), depth + 1, maxDepth);
                }
            }
        } catch (IOException ignored) {}
    }
}
