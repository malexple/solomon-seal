package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Точечная замена фрагмента в файле.
 * Безопаснее write_file — не перезаписывает весь файл.
 */
public class EditFileTool implements AgentTool {

    @Override public String name() { return "edit_file"; }
    @Override public ToolPermission permission() { return ToolPermission.WRITE; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String description() {
        return "Replace exact text in a file. Use for surgical edits without rewriting the whole file.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "path":        {"type": "string",  "description": "File path"},
                "old_content": {"type": "string",  "description": "Exact text to replace"},
                "new_content": {"type": "string",  "description": "Replacement text"},
                "occurrence":  {"type": "integer", "description": "Which occurrence to replace (1=first, -1=last, 0=all). Default: 1"}
              },
              "required": ["path", "old_content", "new_content"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        var pathStr    = (String) args.get("path");
        var oldContent = (String) args.get("old_content");
        var newContent = (String) args.get("new_content");
        int occurrence = args.containsKey("occurrence")
                ? ((Number) args.get("occurrence")).intValue() : 1;

        if (pathStr == null || oldContent == null || newContent == null)
            return CompletableFuture.completedFuture(ToolResult.fail("path, old_content and new_content are required"));

        var path = ctx.resolvePath(pathStr);

        return ctx.confirmationHandler()
                .confirm(name(),
                        "Edit file: " + path.getFileName(),
                        "Replace:\n" + oldContent + "\n\nWith:\n" + newContent)
                .thenApply(confirmed -> {
                    if (!confirmed) return ToolResult.fail("Cancelled by user");
                    try {
                        var original = Files.readString(path);
                        if (!original.contains(oldContent))
                            return ToolResult.fail("old_content not found in file");

                        String result;
                        if (occurrence == 0) {
                            result = original.replace(oldContent, newContent);
                        } else if (occurrence == -1) {
                            int idx = original.lastIndexOf(oldContent);
                            result = original.substring(0, idx) + newContent
                                    + original.substring(idx + oldContent.length());
                        } else {
                            int idx = -1;
                            for (int i = 0; i < occurrence; i++) {
                                idx = original.indexOf(oldContent, idx + 1);
                                if (idx == -1) return ToolResult.fail("Occurrence " + occurrence + " not found");
                            }
                            result = original.substring(0, idx) + newContent
                                    + original.substring(idx + oldContent.length());
                        }
                        Files.writeString(path, result);
                        return ToolResult.ok("Edited: " + path);
                    } catch (Exception e) {
                        return ToolResult.fail("Edit error: " + e.getMessage());
                    }
                });
    }
}