package ru.mcs.llmrobot.core.tools;

import ru.mcs.llmrobot.core.index.IndexManager;
import ru.mcs.llmrobot.core.projects.Project;
import ru.mcs.llmrobot.core.session.Session;

import java.util.List;

/**
 * Контекст выполнения инструмента.
 * Передаётся в каждый AgentTool.execute().
 */
public class ExecutionContext {

    public final Session             session;
    public final List<Project>       projects;
    public final IndexManager        indexManager;
    public final ConfirmationHandler confirmationHandler;

    public ExecutionContext(
            Session session,
            IndexManager indexManager,
            ConfirmationHandler confirmationHandler
    ) {
        this.session             = session;
        this.projects            = session.projects;
        this.indexManager        = indexManager;
        this.confirmationHandler = confirmationHandler;
    }

    /**
     * Резолвит путь: если начинается с "alias:" — ищет проект по алиасу.
     * Иначе возвращает абсолютный путь как есть.
     *
     * Примеры:
     *   "backend:src/main/java/Foo.java" → /home/user/backend/src/main/java/Foo.java
     *   "/home/user/backend/Foo.java"    → /home/user/backend/Foo.java
     */
    public java.nio.file.Path resolvePath(String path) {
        if (path == null) return null;
        int colon = path.indexOf(':');
        // Пропускаем Windows drive letters (C:\...)
        if (colon > 1) {
            var alias = path.substring(0, colon);
            var rel   = path.substring(colon + 1);
            for (var p : projects) {
                if (p.alias.equals(alias)) return p.resolve(rel);
            }
        }
        return java.nio.file.Path.of(path);
    }
}