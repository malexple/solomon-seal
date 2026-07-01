package ru.mcs.solomonseal.core.agent;

import java.util.List;

/**
 * Итог работы AgentLoop.
 */
public record AgentResult(
        String finalAnswer,          // финальный текстовый ответ
        List<String> toolCallLog,    // лог вызовов инструментов для UI
        boolean success,
        String error
) {
    public static AgentResult ok(String answer, List<String> log) {
        return new AgentResult(answer, log, true, null);
    }

    public static AgentResult fail(String error) {
        return new AgentResult(null, List.of(), false, error);
    }
}