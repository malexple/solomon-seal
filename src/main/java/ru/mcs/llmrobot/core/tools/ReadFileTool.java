package ru.mcs.llmrobot.core.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReadFileTool implements Tool {

    @Override public String name() { return "read_file"; }

    @Override public String description() {
        return "Read contents of a file. Optionally specify start_line and end_line (1-based).";
    }

    @Override public String parametersSchema() {
        return """
            {"type":"object","properties":{
              "path":{"type":"string","description":"Absolute or relative file path"},
              "start_line":{"type":"integer","description":"First line (1-based, optional)"},
              "end_line":{"type":"integer","description":"Last line inclusive (optional)"}
            },"required":["path"]}
            """;
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            var pathStr = (String) args.get("path");
            if (pathStr == null) return ToolResult.fail("'path' is required");
            try {
                var path = Path.of(pathStr);
                if (!Files.exists(path)) return ToolResult.fail("File not found: " + pathStr);
                var lines = Files.readAllLines(path);
                int start = args.containsKey("start_line") ? ((Number) args.get("start_line")).intValue() - 1 : 0;
                int end   = args.containsKey("end_line")   ? ((Number) args.get("end_line")).intValue()       : lines.size();
                start = Math.max(0, start);
                end   = Math.min(lines.size(), end);
                var result = String.join("\n", lines.subList(start, end));
                return ToolResult.ok(result);
            } catch (IOException e) {
                return ToolResult.fail("Read error: " + e.getMessage());
            }
        });
    }
}
