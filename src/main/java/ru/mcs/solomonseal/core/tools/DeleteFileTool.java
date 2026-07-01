package ru.mcs.solomonseal.core.tools;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DeleteFileTool implements AgentTool {

    @Override public String name() { return "delete_file"; }
    @Override public ToolPermission permission() { return ToolPermission.WRITE; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String description() {
        return "Delete a file from disk. Requires confirmation.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "File path to delete"}
              },
              "required": ["path"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        var pathStr = (String) args.get("path");
        if (pathStr == null)
            return CompletableFuture.completedFuture(ToolResult.fail("path is required"));

        var path = ctx.resolvePath(pathStr);

        return ctx.confirmationHandler
                .confirm(name(), "Delete file: " + path.getFileName(), path.toString())
                .thenApply(confirmed -> {
                    if (!confirmed) return ToolResult.fail("Cancelled by user");
                    try {
                        if (!Files.exists(path))
                            return ToolResult.fail("File not found: " + pathStr);
                        Files.delete(path);
                        return ToolResult.ok("Deleted: " + path);
                    } catch (Exception e) {
                        return ToolResult.fail("Delete error: " + e.getMessage());
                    }
                });
    }
}