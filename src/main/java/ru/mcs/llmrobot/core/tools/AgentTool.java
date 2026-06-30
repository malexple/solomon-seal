package ru.mcs.llmrobot.core.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Единый интерфейс для всех инструментов агента.
 * Новый инструмент = один класс, реализующий этот интерфейс.
 */
public interface AgentTool {

    /** Имя инструмента (snake_case) — используется в JSON tool_calls */
    String name();

    /** Описание для LLM — что делает инструмент */
    String description();

    /** JSON Schema параметров */
    String parametersSchema();

    /** Уровень доступа */
    ToolPermission permission();

    /** Требует ли явного подтверждения от пользователя */
    boolean requiresConfirmation();

    /**
     * Выполняет инструмент.
     * @param args    параметры из tool_call LLM
     * @param ctx     контекст (проекты, индекс, confirmationHandler)
     */
    CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx);
}