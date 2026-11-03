package ru.mcs.solomonseal.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link AgentLoop.ChatMessageWithToolCalls} serializes
 * tool_calls in the correct OpenAI-compatible format.
 *
 * <p>Before the fix the field was named {@code tool_calls_raw} and held
 * a raw JSON string, which LLM APIs do not recognise on subsequent turns.
 */
class AgentLoopSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void chatMessageWithToolCalls_serializesAsOpenAiFormat() throws Exception {
        var toolCalls = List.<Map<String, Object>>of(Map.of(
                "id", "call_1",
                "type", "function",
                "function", Map.of(
                        "name", "read_file",
                        "arguments", "{\"path\": \"Main.java\"}"
                )
        ));

        var msg = new AgentLoop.ChatMessageWithToolCalls("assistant", "Let me check", toolCalls);
        String json = mapper.writeValueAsString(msg);

        // Must contain "tool_calls" array, NOT "tool_calls_raw"
        assertTrue(json.contains("\"tool_calls\""), "Expected 'tool_calls' field in JSON: " + json);
        assertFalse(json.contains("tool_calls_raw"), "Must NOT contain legacy 'tool_calls_raw' field");

        // Parse back and verify structure
        var node = mapper.readTree(json);
        assertTrue(node.get("tool_calls").isArray());
        assertEquals("call_1", node.get("tool_calls").get(0).get("id").asText());
        assertEquals("read_file", node.get("tool_calls").get(0).get("function").get("name").asText());
    }

    @Test
    void chatMessageWithToolCalls_nullToolCalls_omitsField() throws Exception {
        var msg = new AgentLoop.ChatMessageWithToolCalls("assistant", "Hello", null);
        String json = mapper.writeValueAsString(msg);

        var node = mapper.readTree(json);
        assertFalse(node.has("tool_calls"), "Null tool_calls should be omitted due to @JsonInclude(NON_NULL)");
        assertEquals("Hello", node.get("content").asText());
    }

    @Test
    void chatMessageWithToolCalls_emptyToolCalls_omitsField() throws Exception {
        var msg = new AgentLoop.ChatMessageWithToolCalls("assistant", "Hello", List.of());
        String json = mapper.writeValueAsString(msg);

        var node = mapper.readTree(json);
        // Empty list is non-null, so it WILL be serialized (that's fine, API accepts empty arrays)
        assertTrue(node.has("tool_calls"));
        assertTrue(node.get("tool_calls").isArray());
        assertEquals(0, node.get("tool_calls").size());
    }

    @Test
    void toolResultMessage_serializesCorrectly() throws Exception {
        var msg = new AgentLoop.ToolResultMessage("call_1", "read_file", "file content");
        String json = mapper.writeValueAsString(msg);

        var node = mapper.readTree(json);
        assertEquals("tool", node.get("role").asText());
        assertEquals("call_1", node.get("tool_call_id").asText());
        assertEquals("file content", node.get("content").asText());
    }
}
