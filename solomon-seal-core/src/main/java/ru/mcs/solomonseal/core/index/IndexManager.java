package ru.mcs.solomonseal.core.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mcs.solomonseal.core.projects.Project;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Синглтон-менеджер индексов.
 * Открывает/закрывает ProjectIndex для каждого проекта.
 * Запускает индексацию в фоновом потоке.
 */
public class IndexManager {

    private static final Logger log = LoggerFactory.getLogger(IndexManager.class);

    private final Path baseDir;   // ~/.llm-robot/projects/
    private final ProjectIndexer indexer = new ProjectIndexer();
    private final Map<String, ProjectIndex> openIndexes = new HashMap<>();

    public IndexManager() {
        this.baseDir = Path.of(System.getProperty("user.home"), ".llm-robot", "projects");
    }

    /** Возвращает открытый индекс проекта (создаёт если нет) */
    public ProjectIndex getIndex(Project project) throws SQLException {
        return openIndexes.computeIfAbsent(project.id, id -> {
            var dbPath = baseDir.resolve(id).resolve("index.db");
            try {
                return new ProjectIndex(dbPath);
            } catch (SQLException e) {
                throw new RuntimeException("Cannot open index for " + project.displayName(), e);
            }
        });
    }

    /**
     * Запускает индексацию в фоне.
     * @param project    проект для индексации
     * @param listener   колбэк прогресса (вызывается в фоновом потоке)
     * @param onComplete вызывается по завершении (в фоновом потоке)
     * @param onError    вызывается при ошибке
     */
    public CompletableFuture<Void> indexAsync(
            Project project,
            ProjectIndexer.IndexProgressListener listener,
            Runnable onComplete,
            java.util.function.Consumer<Throwable> onError
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                var index = getIndex(project);
                indexer.index(project.root(), index, project.language, listener);
                project.indexed = true;
                if (onComplete != null) onComplete.run();
            } catch (Exception e) {
                log.error("Indexing failed for {}: {}", project.displayName(), e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    /** Закрывает все открытые индексы */
    public void closeAll() {
        openIndexes.values().forEach(ProjectIndex::close);
        openIndexes.clear();
    }
}