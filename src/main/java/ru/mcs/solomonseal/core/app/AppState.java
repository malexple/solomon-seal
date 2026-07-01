package ru.mcs.solomonseal.core.app;

import ru.mcs.solomonseal.core.config.*;

import java.util.ArrayList;
import java.util.List;

public class AppState {

    private final ConfigManager  configManager;
    private final SecretsManager secretsManager;
    private ru.mcs.solomonseal.core.session.Session activeSession;

    private final List<Runnable> profileChangeListeners = new ArrayList<>();
    private final List<Runnable> sessionChangeListeners = new ArrayList<>();

    public AppState(ConfigManager configManager, SecretsManager secretsManager) {
        this.configManager  = configManager;
        this.secretsManager = secretsManager;
    }

    // ── config ────────────────────────────────────────────────────────────────

    public AppConfig getConfig() {
        return configManager.get();
    }

    public void saveConfig() {
        configManager.save();
    }

    // ── profiles ──────────────────────────────────────────────────────────────

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
        var list = configManager.get().profiles;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(profile.id)) {
                list.set(i, profile);
                configManager.save();
                profileChangeListeners.forEach(Runnable::run);
                return;
            }
        }
        list.add(profile);
        configManager.save();
        profileChangeListeners.forEach(Runnable::run);
    }

    public void deleteProfile(String profileId) {
        configManager.get().profiles.removeIf(p -> p.id.equals(profileId));
        secretsManager.removeApiKey(profileId);
        if (profileId.equals(configManager.get().activeProfileId)) {
            var list = configManager.get().profiles;
            configManager.get().activeProfileId = list.isEmpty() ? null : list.get(0).id;
        }
        configManager.save();
        profileChangeListeners.forEach(Runnable::run);
    }

    // ── secrets ───────────────────────────────────────────────────────────────

    public String getApiKey(String profileId) {
        return secretsManager.getApiKey(profileId);
    }

    public void setApiKey(String profileId, String apiKey) {
        secretsManager.setApiKey(profileId, apiKey);
    }

    // ── session ───────────────────────────────────────────────────────────────

    public ru.mcs.solomonseal.core.session.Session getActiveSession() { return activeSession; }

    public void setActiveSession(ru.mcs.solomonseal.core.session.Session s) {
        this.activeSession = s;
        sessionChangeListeners.forEach(Runnable::run);
    }

    // ── observers ─────────────────────────────────────────────────────────────

    public void onProfileChange(Runnable r) { profileChangeListeners.add(r); }
    public void onSessionChange(Runnable r) { sessionChangeListeners.add(r); }
}