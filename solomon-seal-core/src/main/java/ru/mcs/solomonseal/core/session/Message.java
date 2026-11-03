package ru.mcs.solomonseal.core.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    public String id;
    public Role role;
    public String content;
    public String toolCallId;
    public Map<String, Object> metadata;
    public Instant timestamp;

    public Message() {}

    public static Message user(String content) {
        var m = new Message();
        m.id = java.util.UUID.randomUUID().toString();
        m.role = Role.USER;
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public static Message assistant(String content) {
        var m = new Message();
        m.id = java.util.UUID.randomUUID().toString();
        m.role = Role.ASSISTANT;
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public static Message system(String content) {
        var m = new Message();
        m.id = java.util.UUID.randomUUID().toString();
        m.role = Role.SYSTEM;
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }
}
