package ru.mcs.llmrobot.core.app;

import ru.mcs.llmrobot.core.config.AppConfig;
import ru.mcs.llmrobot.core.config.ConfigManager;
import ru.mcs.llmrobot.core.config.LlmProfile;
import ru.mcs.llmrobot.core.session.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * Central in-memory application state.
 * UI components observe this instead of talking to each other directly.
 */
public class AppState {

    private final ConfigManager configManager;
    private Session activeSession;

    private final List<Runnable> profileChangeListeners = new ArrayList<>();
    private final List<Runnable> sessionChangeListeners = new ArrayList<>();

    public AppState(ConfigManager configManager) {
        this.configManager = configManager;
    }

    // ---- profile ----

    public LlmProfile getActiveProfile() {
        return configManager.get().activeProfile();
    }

    public List<LlmProfile> getProfiles() {
        return configManager.get().profiles;
    }

    public void setActiveProfile(String profileId) {
        configManager.get().activeProfileId = profileId;
        configManager.save();
        profileChangeListeners.forEach(Runnable::run);
    }

    public void saveProfile(LlmProfile profile) {
        var profiles = configManager.get().profiles;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                configManager.save();
                profileChangeListeners.forEach(Runnable::run);
                return;
            }
        }
        // новый профиль
        profiles.add(profile);
        configManager.save();
        profileChangeListeners.forEach(Runnable::run);
    }

    public void deleteProfile(String profileId) {
        configManager.get().profiles.removeIf(p -> p.id.equals(profileId));
        if (profileId.equals(configManager.get().activeProfileId)) {
            var profiles = configManager.get().profiles;
            configManager.get().activeProfileId = profiles.isEmpty() ? null : profiles.get(0).id;
        }
        configManager.save();
        profileChangeListeners.forEach(Runnable::run);
    }

    // ---- session ----

    public Session getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(Session session) {
        this.activeSession = session;
        sessionChangeListeners.forEach(Runnable::run);
    }

    // ---- observers ----

    public void onProfileChange(Runnable listener) {
        profileChangeListeners.add(listener);
    }

    public void onSessionChange(Runnable listener) {
        sessionChangeListeners.add(listener);
    }
}