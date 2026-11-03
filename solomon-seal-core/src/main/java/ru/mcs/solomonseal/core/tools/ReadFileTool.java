package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReadFileTool implements AgentTool {

    @Override public String name() { return "read_file"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Read contents of a file. Optionally specify start_line and end_line (1-based).";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "path":       {"type": "string",  "description": "File path (alias:relative or absolute)"},
                "start_line": {"type": "integer", "description": "First line 1-based (optional)"},
                "end_line":   {"type": "integer", "description": "Last line inclusive (optional)"}
              },
              "required": ["path"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var pathStr = (String) args.get("path");
            if (pathStr == null) return ToolResult.fail("path is required");
            try {
                var path = ctx.resolvePath(pathStr);
                if (!Files.exists(path)) return ToolResult.fail("File not found: " + pathStr);
                var lines = Files.readAllLines(path);
                int start = args.containsKey("start_line")
                        ? ((Number) args.get("start_line")).intValue() - 1 : 0;
                int end   = args.containsKey("end_line")
                        ? ((Number) args.get("end_line")).intValue() : lines.size();
                start = Math.max(0, start);
                end   = Math.min(lines.size(), end);
                return ToolResult.ok(String.join("\n", lines.subList(start, end)));
            } catch (Exception e) {
                return ToolResult.fail("Read error: " + e.getMessage());
            }
        });
    }
}