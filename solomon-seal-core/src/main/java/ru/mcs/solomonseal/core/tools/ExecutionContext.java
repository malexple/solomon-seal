package ru.mcs.solomonseal.core.tools;

import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.projects.Project;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.spi.ConfirmationHandler;

import java.nio.file.Path;
import java.util.List;

/**
 * Контекст выполнения инструмента.
 * Передаётся в каждый AgentTool.execute().
 */
public class ExecutionContext implements ru.mcs.solomonseal.spi.ExecutionContext {

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

    @Override
    public String projectRoot() {
        return projects.isEmpty() ? "" : projects.get(0).rootPath;
    }

    @Override
    public ru.mcs.solomonseal.spi.Session session() {
        return session;
    }

    @Override
    public ConfirmationHandler confirmationHandler() {
        return confirmationHandler;
    }

    /**
     * Резолвит путь: если начинается с "alias:" — ищет проект по алиасу.
     * Иначе возвращает абсолютный путь как есть.
     *
     * Примеры:
     *   "backend:src/main/java/Foo.java" → /home/user/backend/src/main/java/Foo.java
     *   "/home/user/backend/Foo.java"    → /home/user/backend/Foo.java
     */
    @Override
    public Path resolvePath(String path) {
        if (path == null) return null;
        int colon = path.indexOf(':');
        if (colon > 1) {
            var alias = path.substring(0, colon);
            var rel = path.substring(colon + 1);
            for (var p : projects) {
                if (p.alias.equals(alias)) return p.resolve(rel);
            }
        }

        for (var p : projects) {
            if (p.alias.equals(path)) return p.root();
        }
        return Path.of(path);
    }
}
