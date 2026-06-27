package ru.mcs.llmrobot.core.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class GrepTool implements Tool {

    @Override public String name() { return "grep"; }

    @Override public String description() {
        return "Search for a pattern in files under a directory. Returns matching lines with file:line info.";
    }

    @Override public String parametersSchema() {
        return """
            {"type":"object","properties":{
              "pattern":{"type":"string","description":"Text or regex pattern"},
              "dir":{"type":"string","description":"Root directory to search"},
              "glob":{"type":"string","description":"File glob filter, e.g. *.java"},
              "max_results":{"type":"integer","description":"Max lines to return (default 50)"}
            },"required":["pattern","dir"]}
            """;
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            var pattern = (String) args.get("pattern");
            var dir = (String) args.get("dir");
            if (pattern == null || dir == null) return ToolResult.fail("'pattern' and 'dir' are required");
            int maxResults = args.containsKey("max_results") ? ((Number) args.get("max_results")).intValue() : 50;
            String glob = args.containsKey("glob") ? (String) args.get("glob") : "**/*";
            var results = new ArrayList<String>();
            try {
                var regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
                Files.walkFileTree(Path.of(dir), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (results.size() >= maxResults) return FileVisitResult.TERMINATE;
                        if (!matcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;
                        var lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                            if (regex.matcher(lines.get(i)).find()) {
                                results.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir2, BasicFileAttributes attrs) {
                        var name = dir2.getFileName();
                        if (name != null && (name.toString().equals(".git")
                                || name.toString().equals("build")
                                || name.toString().equals("target")
                                || name.toString().equals("node_modules"))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                return ToolResult.ok(results.isEmpty() ? "No matches found." : String.join("\n", results));
            } catch (IOException e) {
                return ToolResult.fail("Grep error: " + e.getMessage());
            }
        });
    }
}
