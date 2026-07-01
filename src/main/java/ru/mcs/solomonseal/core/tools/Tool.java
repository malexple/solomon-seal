package ru.mcs.solomonseal.core.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Tool {
    String name();
    String description();
    /** JSON Schema of the parameters */
    String parametersSchema();
    CompletableFuture<ToolResult> execute(Map<String, Object> args);
}
