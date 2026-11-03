package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class GrepTool implements AgentTool {

    @Override public String name() { return "grep"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Search for a pattern in files under a directory. Returns matching lines with file:line info.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "pattern":     {"type": "string",  "description": "Text or regex pattern"},
                "dir":         {"type": "string",  "description": "Root directory to search"},
                "glob":        {"type": "string",  "description": "File glob filter, e.g. *.java"},
                "max_results": {"type": "integer", "description": "Max lines to return (default 50)"}
              },
              "required": ["pattern", "dir"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var pattern    = (String) args.get("pattern");
            var dirStr     = (String) args.get("dir");
            if (pattern == null || dirStr == null)
                return ToolResult.fail("pattern and dir are required");

            int maxResults = args.containsKey("max_results")
                    ? ((Number) args.get("max_results")).intValue() : 50;
            String glob = args.containsKey("glob") ? (String) args.get("glob") : "*.{java,kt,py,js,ts,xml,md}";

            var results = new ArrayList<String>();
            try {
                var regex   = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
                var root    = ctx.resolvePath(dirStr);

                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (results.size() >= maxResults) return FileVisitResult.TERMINATE;
                        if (!matcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;
                        var lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                            if (regex.matcher(lines.get(i)).find())
                                results.add(file + ":" + (i + 1) + "  " + lines.get(i).trim());
                        }
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
                return ToolResult.fail("Grep error: " + e.getMessage());
            }
            return results.isEmpty()
                    ? ToolResult.fail("No matches found")
                    : ToolResult.ok(String.join("\n", results));
        });
    }
}