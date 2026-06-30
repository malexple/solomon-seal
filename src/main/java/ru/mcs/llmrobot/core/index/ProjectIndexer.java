package ru.mcs.llmrobot.core.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сканирует проект, строит SQLite индекс.
 * Инкрементальный: пересканирует только изменённые файлы (по SHA-256).
 */
public class ProjectIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProjectIndexer.class);

    private static final List<String> SKIP_DIRS = List.of(
            ".git", ".gradle", ".idea", "build", "target",
            "node_modules", "out", ".mvn"
    );

    // Реестр языковых индексаторов — добавить новый язык = одна строка
    private final Map<String, LanguageIndexer> indexers = new HashMap<>();

    public ProjectIndexer() {
        register(new JavaLanguageIndexer());
        // register(new KotlinLanguageIndexer());  ← добавишь потом
        // register(new PythonLanguageIndexer());
    }

    public void register(LanguageIndexer indexer) {
        for (var ext : indexer.fileExtensions()) {
            indexers.put(ext, indexer);
        }
    }

    /**
     * Индексирует проект. Можно вызывать повторно — обновит только изменённые файлы.
     *
     * @param projectRoot  корневая папка проекта
     * @param index        открытый ProjectIndex (SQLite)
     * @param language     фильтр языка ("java", "kotlin" — или null для всех)
     * @param listener     колбэк прогресса (файл, текущий, всего)
     */
    public void index(Path projectRoot, ProjectIndex index, String language,
                      IndexProgressListener listener) throws IOException, SQLException {

        var files = collectFiles(projectRoot, language);
        int total = files.size();
        int current = 0;

        log.info("Indexing {} files in {}", total, projectRoot);

        for (var file : files) {
            current++;
            if (listener != null) {
                listener.onProgress(file.getFileName().toString(), current, total);
            }
            try {
                indexFile(file, index);
            } catch (Exception e) {
                log.warn("Failed to index {}: {}", file, e.getMessage());
            }
        }

        log.info("Indexing complete: {}", index.stats());  // stats() бросает SQLException
    }

    private void indexFile(Path file, ProjectIndex index) throws Exception {
        var path = file.toAbsolutePath().toString();
        var size = Files.size(file);
        var hash = sha256(file);

        // Инкрементальная проверка: если hash совпадает — пропускаем
        var existingHash = index.getFileHash(path);
        if (existingHash.isPresent() && existingHash.get().equals(hash)) {
            return;
        }

        var ext = getExtension(file);
        var indexer = indexers.get(ext);
        var language = indexer != null ? indexer.language() : "unknown";

        // Upsert файла
        var fileRecord = new FileRecord(path, language, size, hash);
        var fileId = index.upsertFile(fileRecord);

        // Удаляем старые символы файла
        index.deleteSymbolsForFile(fileId);

        // Парсим символы
        if (indexer != null) {
            var symbols = indexer.index(file);
            for (var symbol : symbols) {
                index.insertSymbol(fileId, symbol);
            }
        }

        // FTS: сохраняем содержимое файла для полнотекстового поиска
        var content = Files.readString(file);
        index.upsertFts(path, content);
    }

    private List<Path> collectFiles(Path root, String language) throws IOException {
        var result = new ArrayList<Path>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                var name = dir.getFileName().toString();
                return SKIP_DIRS.contains(name)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                var ext = getExtension(file);
                if (!indexers.containsKey(ext)) return FileVisitResult.CONTINUE;
                // Фильтр по языку если указан
                if (language != null) {
                    var indexer = indexers.get(ext);
                    if (!indexer.language().equals(language)) return FileVisitResult.CONTINUE;
                }
                result.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private String getExtension(Path file) {
        var name = file.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        var bytes = digest.digest();
        var sb = new StringBuilder();
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @FunctionalInterface
    public interface IndexProgressListener {
        void onProgress(String currentFile, int current, int total);
    }
}