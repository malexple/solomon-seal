package ru.mcs.solomonseal.spi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AgentTool {
    String name();
    String description();
    String parametersSchema();
    ToolPermission permission();
    boolean requiresConfirmation();
    CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx);
}
