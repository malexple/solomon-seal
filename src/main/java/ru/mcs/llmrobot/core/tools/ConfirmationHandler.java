package ru.mcs.llmrobot.core.tools;

import java.util.concurrent.CompletableFuture;

/**
 * UI-агностичный интерфейс запроса подтверждения у пользователя.
 * JavaFX реализует через Alert, TUI — через stdin.
 */
public interface ConfirmationHandler {

    /**
     * Запрашивает подтверждение асинхронно.
     * @param toolName   имя инструмента
     * @param description краткое описание действия
     * @param details    полный текст (команда, путь и т.д.)
     * @return true = разрешено, false = отклонено
     */
    CompletableFuture<Boolean> confirm(String toolName, String description, String details);
}