// В stream() и chat() — меняем сигнатуру: добавляем apiKey параметр
// или проще — передаём profile + apiKey как единый объект RuntimeProfile

// Создай простой value-object:
package ru.mcs.solomonseal.core.llm;

/** Полный runtime-профиль: публичные данные + секрет для одного запроса. */

import ru.mcs.solomonseal.core.config.LlmProfile;

/** Полный runtime-профиль: публичные данные + секрет для одного запроса. */
public record RuntimeProfile(String baseUrl, String apiKey, String modelId, boolean trustSelfSignedCert) {

    public static RuntimeProfile of(LlmProfile profile, String apiKey) {
        return new RuntimeProfile(profile.baseUrl, apiKey, profile.modelId, profile.trustSelfSignedCert);
    }
}