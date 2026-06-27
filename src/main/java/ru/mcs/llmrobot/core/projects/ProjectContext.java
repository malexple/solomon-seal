package ru.mcs.llmrobot.core.projects;

import java.nio.file.Path;

public class ProjectContext {
    public String alias;
    public Path root;
    public String description;

    public ProjectContext() {}

    public ProjectContext(String alias, Path root, String description) {
        this.alias = alias;
        this.root = root;
        this.description = description;
    }

    /**
     * Resolves a path like "backend:src/main/java/Foo.java" to an absolute Path.
     * If the input has no alias prefix, resolves against this project's root directly.
     */
    public Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }
}
