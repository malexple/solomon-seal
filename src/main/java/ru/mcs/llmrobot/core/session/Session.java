package ru.mcs.llmrobot.core.session;

import ru.mcs.llmrobot.core.context.ContextProfile;
import ru.mcs.llmrobot.core.projects.ProjectContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Session {
    public String id;
    public String title;
    public SessionMode mode;
    public String profileId;              // ← ссылка на LlmProfile.id
    public List<Message> history;
    public List<ProjectContext> projects;
    public ContextProfile contextProfile;
    public Instant createdAt;
    public Instant updatedAt;

    public Session() {
        this.id = UUID.randomUUID().toString();
        this.history = new ArrayList<>();
        this.projects = new ArrayList<>();
        this.mode = SessionMode.ASK;
        this.contextProfile = ContextProfile.MINIMAL;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Session(String title) {
        this();
        this.title = title;
    }

    public void addMessage(Message msg) {
        history.add(msg);
        updatedAt = Instant.now();
    }

    public void addProject(ProjectContext project) {
        projects.add(project);
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}