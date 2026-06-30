package ru.mcs.llmrobot.core.projects;

import java.nio.file.Path;
import java.util.UUID;

public class Project {
    public String id;
    public String alias;          // короткое имя: "backend", "frontend"
    public String rootPath;       // строка для Jackson (Path не сериализуется)
    public String description;
    public String language;       // "java", "kotlin", "python" — для индексатора
    public boolean indexed;       // был ли проиндексирован

    public Project() {}

    public Project(String alias, String rootPath, String language) {
        this.id          = UUID.randomUUID().toString();
        this.alias       = alias;
        this.rootPath    = rootPath;
        this.language    = language;
        this.indexed     = false;
    }

    /** Возвращает Path из строкового rootPath */
    public Path root() {
        return Path.of(rootPath);
    }

    /** Резолвит относительный путь к файлу в абсолютный */
    public Path resolve(String relativePath) {
        return root().resolve(relativePath).normalize();
    }

    /** Отображаемое имя для UI */
    public String displayName() {
        return alias != null && !alias.isBlank() ? alias : root().getFileName().toString();
    }

    @Override
    public String toString() {
        return displayName();
    }
}