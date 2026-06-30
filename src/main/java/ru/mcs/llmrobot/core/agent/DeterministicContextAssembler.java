package ru.mcs.llmrobot.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mcs.llmrobot.core.index.IndexManager;
import ru.mcs.llmrobot.core.projects.Project;
import ru.mcs.llmrobot.core.session.Session;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * Fallback когда оркестратор-LLM не настроен.
 * Детерминированно собирает контекст из SQLite индекса:
 *   1. Структура проекта (дерево файлов)
 *   2. Символы из индекса (классы, методы)
 *   3. Результаты FTS по тексту запроса
 */
public class DeterministicContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(DeterministicContextAssembler.class);
    private static final int MAX_SYMBOLS = 40;
    private static final int MAX_FTS     = 10;

    private final IndexManager indexManager;

    public DeterministicContextAssembler(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    /**
     * Собирает контекст для запроса пользователя.
     * Возвращает строку, которая добавляется как system-сообщение в запрос к LLM.
     */
    public CompletableFuture<String> assemble(Session session, String userQuery) {
        return CompletableFuture.supplyAsync(() -> {
            var sb = new StringBuilder();
            sb.append("# Project Context\n\n");

            for (var project : session.projects) {
                if (!project.indexed) continue;
                sb.append("## ").append(project.displayName()).append("\n");
                appendSymbols(sb, project, userQuery);
                appendFts(sb, project, userQuery);
                sb.append("\n");
            }

            return sb.toString().trim();
        });
    }

    private void appendSymbols(StringBuilder sb, Project project, String query) {
        try {
            var index   = indexManager.getIndex(project);
            var symbols = index.findSymbols(query);
            if (symbols.isEmpty()) return;

            sb.append("### Relevant symbols\n");
            symbols.stream().limit(MAX_SYMBOLS).forEach(s ->
                    sb.append("- ").append(s.kind).append("  ")
                            .append(s.signature != null ? s.signature : s.name)
                            .append(s.parentName != null ? "  (in " + s.parentName + ")" : "")
                            .append("\n")
            );
        } catch (SQLException e) {
            log.warn("Symbol search failed for {}: {}", project.displayName(), e.getMessage());
        }
    }

    private void appendFts(StringBuilder sb, Project project, String query) {
        try {
            var index = indexManager.getIndex(project);
            var paths = index.searchContent(query, MAX_FTS);
            if (paths.isEmpty()) return;

            sb.append("### Files containing '").append(query).append("'\n");
            paths.forEach(p -> sb.append("- ").append(p).append("\n"));
        } catch (SQLException e) {
            log.warn("FTS search failed for {}: {}", project.displayName(), e.getMessage());
        }
    }
}