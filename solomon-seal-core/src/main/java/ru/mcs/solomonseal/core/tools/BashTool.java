package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Выполняет команду через ProcessBuilder (кроссплатформенно).
 * Всегда требует подтверждения.
 */
public class BashTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_CHARS = 8000;

    @Override public String name() { return "bash"; }
    @Override public ToolPermission permission() { return ToolPermission.EXEC; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String description() {
        return "Execute a shell command. Works on Windows, Linux, macOS. Always requires confirmation.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "command": {"type": "string",  "description": "Command to execute"},
                "workdir": {"type": "string",  "description": "Working directory (optional, defaults to first project root)"},
                "timeout": {"type": "integer", "description": "Timeout in seconds (default 60)"}
              },
              "required": ["command"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        var command = (String) args.get("command");
        if (command == null || command.isBlank())
            return CompletableFuture.completedFuture(ToolResult.fail("command is required"));

        var workdirStr = (String) args.get("workdir");
        int timeout = args.containsKey("timeout")
                ? ((Number) args.get("timeout")).intValue() : TIMEOUT_SECONDS;

        // Рабочая директория: явная или первый проект сессии
        Path workdir = null;
        if (workdirStr != null) {
            workdir = ctx.resolvePath(workdirStr);
        } else if (!((ExecutionContext) ctx).projects.isEmpty()) {
            workdir = ((ExecutionContext) ctx).projects.get(0).root();
        }
        final Path finalWorkdir = workdir;

        return ctx.confirmationHandler()
                .confirm(name(), "Execute command", command)
                .thenApplyAsync(confirmed -> {
                    if (!confirmed) return ToolResult.fail("Cancelled by user");
                    try {
                        return runCommand(command, finalWorkdir, timeout);
                    } catch (Exception e) {
                        return ToolResult.fail("Execution error: " + e.getMessage());
                    }
                });
    }

    private ToolResult runCommand(String command, Path workdir, int timeoutSec) throws Exception {
        // Кроссплатформенный запуск
        List<String> cmd = isWindows()
                ? List.of("cmd.exe", "/c", command)
                : List.of("/bin/sh", "-c", command);

        var pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (workdir != null) pb.directory(workdir.toFile());

        var process = pb.start();
        var sb = new StringBuilder();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                if (sb.length() > MAX_OUTPUT_CHARS) {
                    sb.append("\n[output truncated]");
                    break;
                }
            }
        }

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolResult.fail("Command timed out after " + timeoutSec + "s");
        }

        int exitCode = process.exitValue();
        var output = sb.toString().trim();

        if (exitCode != 0) {
            return ToolResult.fail("Exit code " + exitCode + ":\n" + output);
        }
        return ToolResult.ok(output.isEmpty() ? "(no output)" : output);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}