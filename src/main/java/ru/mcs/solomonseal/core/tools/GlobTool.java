package ru.mcs.solomonseal.core.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GlobTool implements AgentTool {

    @Override public String name() { return "glob"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Find files matching a glob pattern under a directory.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "pattern":     {"type": "string",  "description": "Glob pattern, e.g. **/*.java"},
                "dir":         {"type": "string",  "description": "Root directory"},
                "max_results": {"type": "integer", "description": "Max results (default 100)"}
              },
              "required": ["pattern", "dir"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var pattern = (String) args.get("pattern");
            var dirStr  = (String) args.get("dir");
            if (pattern == null || dirStr == null)
                return ToolResult.fail("pattern and dir are required");

            int max  = args.containsKey("max_results")
                    ? ((Number) args.get("max_results")).intValue() : 100;
            var root    = ctx.resolvePath(dirStr);
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            var found   = new ArrayList<String>();

            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (found.size() >= max) return FileVisitResult.TERMINATE;
                        if (matcher.matches(root.relativize(file)))
                            found.add(file.toString());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        var n = dir.getFileName().toString();
                        return (n.equals(".git") || n.equals("build") || n.equals("target")
                                || n.equals("node_modules")) ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                return ToolResult.fail("Glob error: " + e.getMessage());
            }
            return found.isEmpty()
                    ? ToolResult.fail("No files found")
                    : ToolResult.ok(String.join("\n", found));
        });
    }
}