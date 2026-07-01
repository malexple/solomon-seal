package ru.mcs.solomonseal.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mcs.solomonseal.core.config.AppConfig;
import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.core.tools.ConfirmationHandler;
import ru.mcs.solomonseal.core.tools.ExecutionContext;
import ru.mcs.solomonseal.core.tools.ToolRegistry;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Точка входа для агентского режима.
 * Выбирает AgentLoop (если оркестратор настроен)
 * или DeterministicContextAssembler (fallback).
 */
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private static final String SYSTEM_PROMPT = """
        You are an expert coding assistant with access to tools for reading and modifying code.
        Always think step by step:
        1. Use read_file / find_symbol / search_code to understand the codebase
        2. Plan your changes before writing
        3. Use edit_file for surgical changes, write_file only for new files
        4. After changes, verify with read_file
        Respond concisely. When done, give a summary of what you did.
        """;

    private final AppConfig                     appConfig;
    private final ToolRegistry                  toolRegistry;
    private final IndexManager                  indexManager;
    private final DeterministicContextAssembler deterministicAssembler;

    public OrchestratorService(
            AppConfig appConfig,
            ToolRegistry toolRegistry,
            IndexManager indexManager
    ) {
        this.appConfig              = appConfig;
        this.toolRegistry           = toolRegistry;
        this.indexManager           = indexManager;
        this.deterministicAssembler = new DeterministicContextAssembler(indexManager);
    }

    /**
     * Основной метод — запускает агентский режим или fallback.
     *
     * @param session    текущая сессия
     * @param userQuery  запрос пользователя
     * @param handler    confirmationHandler из UI
     * @param onStep     колбэк прогресса шагов агента
     * @return результат
     */
    public CompletableFuture<AgentResult> run(
            Session session,
            String userQuery,
            ConfirmationHandler handler,
            Consumer<String> onStep
    ) {
        var ctx = new ExecutionContext(session, indexManager, handler);

        if (appConfig.orchestrator.enabled) {
            var systemPrompt = buildSystemPrompt(session);  // ← динамический промпт
            var loop = new AgentLoop(appConfig.orchestrator, toolRegistry);
            return loop.run(systemPrompt, userQuery, session.mode, ctx, onStep);
        } else {
            return deterministicAssembler
                    .assemble(session, userQuery)
                    .thenApply(context -> AgentResult.ok(context, List.of(
                            "ℹ Deterministic context assembled (no orchestrator LLM configured)"
                    )));
        }
    }

    private String buildSystemPrompt(Session session) {
        var sb = new StringBuilder();
        sb.append("""
        You are an expert coding assistant with access to tools for reading and modifying code.
        
        ## Tool usage strategy (follow this order):
        1. ALWAYS start with `find_symbol` or `search_code` to locate relevant code quickly
        2. Use `list_tree` only if you need to understand project structure
        3. Use `read_file` to read specific files found via find_symbol/search_code
        4. Use `edit_file` for surgical edits, `write_file` only for new files
        5. After changes verify with `read_file`
        
        ## Available tools (prefer DB tools — they are fast):
        - `find_symbol` — search classes/methods/fields in SQLite index (FAST, use first)
        - `search_code` — full-text search across all indexed files (FAST, use first)
        - `list_tree`   — show directory structure (slow, use only when needed)
        - `read_file`   — read file content by path
        - `grep`        — regex search in files (slower than search_code)
        - `glob`        — find files by pattern
        - `bash`        — execute shell command (requires confirmation)
        
        Respond in the same language as the user's message.
        When done, give a concise summary of what you found or did.
        
        """);

        if (!session.projects.isEmpty()) {
            sb.append("## Available projects\n");
            for (var p : session.projects) {
                sb.append("- alias: `").append(p.alias)
                        .append("`  path: `").append(p.rootPath)
                        .append("`  language: ").append(p.language);
                if (p.indexed) {
                    sb.append("  **[indexed — use find_symbol/search_code]**");
                } else {
                    sb.append("  [not indexed — use list_tree/read_file]");
                }
                sb.append("\n");
            }
            sb.append("\nUse `alias:relative/path` format, e.g. `note:src/Main.java`\n");
        }

        return sb.toString();
    }
}