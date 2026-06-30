package ru.mcs.llmrobot.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mcs.llmrobot.core.config.AppConfig;
import ru.mcs.llmrobot.core.index.IndexManager;
import ru.mcs.llmrobot.core.session.Session;
import ru.mcs.llmrobot.core.session.SessionMode;
import ru.mcs.llmrobot.core.tools.ConfirmationHandler;
import ru.mcs.llmrobot.core.tools.ExecutionContext;
import ru.mcs.llmrobot.core.tools.ToolRegistry;

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
        Always use tools to explore the codebase before answering — never guess file contents.
        Think step by step:
        1. Use list_tree to see project structure
        2. Use read_file / find_symbol / search_code to understand specific parts
        3. Plan changes before writing
        4. Use edit_file for surgical edits, write_file only for new files
        5. After changes verify with read_file
        Respond concisely. When done, give a summary of what you did.
        
        """);

        // ← Добавляем проекты — LLM теперь знает пути и алиасы
        if (!session.projects.isEmpty()) {
            sb.append("## Available projects\n");
            for (var p : session.projects) {
                sb.append("- alias: `").append(p.alias)
                        .append("`  path: `").append(p.rootPath)
                        .append("`  language: ").append(p.language)
                        .append(p.indexed ? "  [indexed]" : "  [not indexed]")
                        .append("\n");
            }
            sb.append("\nUse `alias:relative/path` format for file paths, e.g. `note:src/Main.java`\n");
            sb.append("Or use the full path directly.\n");
        }

        return sb.toString();
    }
}