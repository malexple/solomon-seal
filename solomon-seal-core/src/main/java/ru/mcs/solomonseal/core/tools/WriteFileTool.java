package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WriteFileTool implements AgentTool {

    @Override public String name() { return "write_file"; }
    @Override public ToolPermission permission() { return ToolPermission.WRITE; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String description() {
        return "Create or overwrite a file with given content.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "path":    {"type": "string", "description": "File path (alias:relative or absolute)"},
                "content": {"type": "string", "description": "Full file content"}
              },
              "required": ["path", "content"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        var pathStr  = (String) args.get("path");
        var content  = (String) args.get("content");
        if (pathStr == null || content == null)
            return CompletableFuture.completedFuture(ToolResult.fail("path and content are required"));

        var path = ctx.resolvePath(pathStr);

        return ctx.confirmationHandler()
                .confirm(name(), "Write file: " + path.getFileName(), path.toString())
                .thenApply(confirmed -> {
                    if (!confirmed) return ToolResult.fail("Cancelled by user");
                    try {
                        Files.createDirectories(path.getParent());
                        Files.writeString(path, content);
                        return ToolResult.ok("Written: " + path);
                    } catch (Exception e) {
                        return ToolResult.fail("Write error: " + e.getMessage());
                    }
                });
    }
}