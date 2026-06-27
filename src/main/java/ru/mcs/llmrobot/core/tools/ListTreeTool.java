package ru.mcs.llmrobot.core.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ListTreeTool implements Tool {

    @Override public String name() { return "list_tree"; }
    @Override public String description() { return "List directory tree up to given depth."; }
    @Override public String parametersSchema() {
        return """
            {"type":"object","properties":{
              "dir":{"type":"string"},
              "depth":{"type":"integer","description":"Max depth (default 3)"}
            },"required":["dir"]}""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            var dir = (String) args.get("dir");
            int depth = args.containsKey("depth") ? ((Number) args.get("depth")).intValue() : 3;
            var sb = new StringBuilder();
            try {
                appendTree(sb, Path.of(dir), "", 0, depth);
                return ToolResult.ok(sb.toString());
            } catch (IOException e) {
                return ToolResult.fail("ListTree error: " + e.getMessage());
            }
        });
    }

    private void appendTree(StringBuilder sb, Path dir, String prefix, int depth, int maxDepth) throws IOException {
        if (depth >= maxDepth) return;
        try (var stream = Files.list(dir)) {
            var entries = stream
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .filter(p -> {
                    var n = p.getFileName().toString();
                    return !n.equals("build") && !n.equals("target") && !n.equals("node_modules");
                })
                .sorted().toList();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var last = (i == entries.size() - 1);
                sb.append(prefix).append(last ? "└── " : "├── ").append(entry.getFileName()).append("\n");
                if (Files.isDirectory(entry)) {
                    appendTree(sb, entry, prefix + (last ? "    " : "│   "), depth + 1, maxDepth);
                }
            }
        }
    }
}
