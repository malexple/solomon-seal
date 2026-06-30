// В stream() и chat() — меняем сигнатуру: добавляем apiKey параметр
// или проще — передаём profile + apiKey как единый объект RuntimeProfile

// Создай простой value-object:
package ru.mcs.llmrobot.core.llm;

/** Полный runtime-профиль: публичные данные + секрет для одного запроса. */
public record RuntimeProfile(String baseUrl, String apiKey, String modelId) {
    public static RuntimeProfile of(
            ru.mcs.llmrobot.core.config.LlmProfile profile,
            String apiKey
    ) {
        return new RuntimeProfile(profile.baseUrl, apiKey, profile.modelId);
    }
}