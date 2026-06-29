package ru.mcs.llmrobot.core.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public List<LlmProfile> profiles = new ArrayList<>();
    public String activeProfileId = null;
    public String theme = "dark";

    public AppConfig() {
        // дефолтный профиль при первом запуске
        var ollama = new LlmProfile(
                "Ollama local",
                "http://localhost:11434/v1",
                "ollama",
                "qwen2.5-coder:32b"
        );
        profiles.add(ollama);
        activeProfileId = ollama.id;
    }

    public LlmProfile activeProfile() {
        if (activeProfileId == null) return profiles.isEmpty() ? null : profiles.get(0);
        return profiles.stream()
                .filter(p -> p.id.equals(activeProfileId))
                .findFirst()
                .orElse(profiles.isEmpty() ? null : profiles.get(0));
    }
}