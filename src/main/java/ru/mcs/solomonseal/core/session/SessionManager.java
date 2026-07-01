package ru.mcs.solomonseal.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Manages sessions: create, load, save, list.
 * Storage: ~/.llm-robot/sessions/<id>.json
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private final Path sessionsDir;
    private final ObjectMapper mapper;
    private final List<Session> sessions = new ArrayList<>();

    public SessionManager() {
        this(defaultSessionsDir());
    }

    public SessionManager(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        loadAll();
    }

    private static Path defaultSessionsDir() {
        return Path.of(System.getProperty("user.home"), ".llm-robot", "sessions");
    }

    private void loadAll() {
        try {
            Files.createDirectories(sessionsDir);
            try (var stream = Files.list(sessionsDir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                      .forEach(p -> {
                          try {
                              var s = mapper.readValue(p.toFile(), Session.class);
                              sessions.add(s);
                          } catch (IOException e) {
                              log.warn("Failed to load session from {}: {}", p, e.getMessage());
                          }
                      });
            }
            sessions.sort(Comparator.comparing((Session s) -> s.updatedAt).reversed());
        } catch (IOException e) {
            log.error("Failed to initialize sessions directory: {}", e.getMessage());
        }
    }

    public Session create(String title) {
        var session = new Session(title);
        sessions.add(0, session);
        save(session);
        return session;
    }

    public void save(Session session) {
        session.touch();
        try {
            Files.createDirectories(sessionsDir);
            mapper.writeValue(sessionsDir.resolve(session.id + ".json").toFile(), session);
        } catch (IOException e) {
            log.error("Failed to save session {}: {}", session.id, e.getMessage());
        }
    }

    public Optional<Session> findById(String id) {
        return sessions.stream().filter(s -> s.id.equals(id)).findFirst();
    }

    public List<Session> getAll() {
        return List.copyOf(sessions);
    }

    public void delete(String sessionId) {
        sessions.removeIf(s -> s.id.equals(sessionId));
        try {
            Files.deleteIfExists(sessionsDir.resolve(sessionId + ".json"));
        } catch (IOException e) {
            log.warn("Failed to delete session file {}", sessionId);
        }
    }
}
