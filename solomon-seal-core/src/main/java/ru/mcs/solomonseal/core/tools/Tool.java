package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Tool {
    String name();
    String description();
    /** JSON Schema of the parameters */
    String parametersSchema();
    CompletableFuture<ToolResult> execute(Map<String, Object> args);
}
