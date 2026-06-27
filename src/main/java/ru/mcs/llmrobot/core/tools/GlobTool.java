package ru.mcs.llmrobot.core.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GlobTool implements Tool {

    @Override public String name() { return "glob"; }
    @Override public String description() { return "Find files matching a glob pattern under a directory."; }
    @Override public String parametersSchema() {
        return """
            {"type":"object","properties":{
              "pattern":{"type":"string","description":"Glob pattern, e.g. **/*.java"},
              "dir":{"type":"string","description":"Root directory"},
              "max_results":{"type":"integer"}
            },"required":["pattern","dir"]}""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            var pattern = (String) args.get("pattern");
            var dir = (String) args.get("dir");
            if (pattern == null || dir == null) return ToolResult.fail("'pattern' and 'dir' are required");
            int max = args.containsKey("max_results") ? ((Number) args.get("max_results")).intValue() : 100;
            var found = new ArrayList<String>();
            try {
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                var root = Path.of(dir);
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                        if (found.size() >= max) return FileVisitResult.TERMINATE;
                        if (matcher.matches(root.relativize(file))) found.add(file.toString());
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                        var n = d.getFileName() == null ? "" : d.getFileName().toString();
                        return (n.equals(".git") || n.equals("build") || n.equals("target") || n.equals("node_modules"))
                                ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }
                });
                return ToolResult.ok(found.isEmpty() ? "No files found." : String.join("\n", found));
            } catch (IOException e) {
                return ToolResult.fail("Glob error: " + e.getMessage());
            }
        });
    }
}
