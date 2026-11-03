package ru.mcs.solomonseal.core.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolCallParser}.
 *
 * <p>Covers all three fallback levels:
 * <ol>
 *   <li>Native OpenAI format (choices[0].message.tool_calls[])</li>
 *   <li>Text-based fallbacks ({@code <tool_call>}, {@code ```json}, bare JSON)</li>
 *   <li>Streaming accumulator</li>
 * </ol>
 */
class ToolCallParserTest {

    private final ToolCallParser parser = new ToolCallParser();

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Native OpenAI format
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void parseResponse_singleToolCall() {
        String json = """
            {
              "choices": [{
                "message": {
                  "content": null,
                  "tool_calls": [{
                    "id": "call_abc123",
                    "type": "function",
                    "function": {
                      "name": "read_file",
                      "arguments": "{\\"path\\": \\"src/Main.java\\"}"
                    }
                  }]
                }
              }]
            }
            """;

        var calls = parser.parseResponse(json);

        assertEquals(1, calls.size());
        assertEquals("call_abc123", calls.get(0).callId());
        assertEquals("read_file", calls.get(0).toolName());
        assertEquals("src/Main.java", calls.get(0).args().get("path"));
    }

    @Test
    void parseResponse_multipleToolCalls() {
        String json = """
            {
              "choices": [{
                "message": {
                  "tool_calls": [
                    {"id": "call_1", "function": {"name": "read_file", "arguments": "{}"}},
                    {"id": "call_2", "function": {"name": "write_file", "arguments": "{\\"content\\": \\"hi\\"}"}}
                  ]
                }
              }]
            }
            """;

        var calls = parser.parseResponse(json);

        assertEquals(2, calls.size());
        assertEquals("read_file", calls.get(0).toolName());
        assertEquals("write_file", calls.get(1).toolName());
    }

    @Test
    void parseResponse_noToolCalls_returnsEmptyList() {
        String json = """
            {"choices": [{"message": {"content": "Hello!"}}]}
            """;

        var calls = parser.parseResponse(json);

        assertTrue(calls.isEmpty());
    }

    @Test
    void parseResponse_malformedJson_returnsEmptyList() {
        var calls = parser.parseResponse("not json at all");
        assertTrue(calls.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Text-based fallbacks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void parseFromText_toolCallTagFormat() {
        String text = """
            I'll read the file for you.
            <tool_call>{"name": "read_file", "arguments": {"path": "src/Main.java"}}</tool_call>
            """;

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("read_file", calls.get(0).toolName());
        assertEquals("src/Main.java", calls.get(0).args().get("path"));
        // UUID is generated for fallback, so just check it's non-null
        assertNotNull(calls.get(0).callId());
    }

    @Test
    void parseFromText_codeBlockJsonFormat() {
        String text = """
            ```json
            {"name": "grep", "arguments": {"pattern": "class Main", "path": "src"}}
            ```
            """;

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("grep", calls.get(0).toolName());
        assertEquals("class Main", calls.get(0).args().get("pattern"));
    }

    @Test
    void parseFromText_bareJsonFormat() {
        String text = "Some text before {\"name\": \"bash\", \"arguments\": {\"command\": \"mvn test\"}} and after";

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("bash", calls.get(0).toolName());
        assertEquals("mvn test", calls.get(0).args().get("command"));
    }

    @Test
    void parseFromText_functionWrapperFormat() {
        // Some models wrap in {"function": {...}} even in text
        String text = """
            <tool_call>{"function": {"name": "edit_file", "arguments": {"path": "A.java", "old": "x", "new": "y"}}}</tool_call>
            """;

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("edit_file", calls.get(0).toolName());
        assertEquals("A.java", calls.get(0).args().get("path"));
    }

    @Test
    void parseFromText_multipleToolCalls() {
        String text = """
            <tool_call>{"name": "read_file", "arguments": {"path": "A.java"}}</tool_call>
            <tool_call>{"name": "read_file", "arguments": {"path": "B.java"}}</tool_call>
            """;

        var calls = parser.parseFromText(text);

        assertEquals(2, calls.size());
        assertEquals("A.java", calls.get(0).args().get("path"));
        assertEquals("B.java", calls.get(1).args().get("path"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "No tools here", "null"})
    void parseFromText_emptyOrNoTools_returnsEmptyList(String text) {
        var calls = parser.parseFromText(text);
        assertTrue(calls.isEmpty());
    }

    @Test
    void parseFromText_malformedJsonInsideTag_returnsEmptyList() {
        String text = "<tool_call>not valid json</tool_call>";
        var calls = parser.parseFromText(text);
        assertTrue(calls.isEmpty());
    }

    @Test
    void parseFromText_nestedBracesInBareJson() {
        // Ensure brace matching handles nested objects
        String text = "{\"name\": \"write_file\", \"arguments\": {\"path\": \"x\", \"content\": \"{\\\"a\\\": 1}\"}}";

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("write_file", calls.get(0).toolName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Streaming accumulator
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void parseAccumulated_completeToolCall() {
        Map<String, ToolCallParser.ToolCallAccumulator> acc = Map.of(
                "0", acc("call_1", "read_file", "{\"path\": \"Main.java\"}")
        );

        var calls = parser.parseAccumulated(acc);

        assertEquals(1, calls.size());
        assertEquals("call_1", calls.get(0).callId());
        assertEquals("read_file", calls.get(0).toolName());
        assertEquals("Main.java", calls.get(0).args().get("path"));
    }

    @Test
    void parseAccumulated_incompleteToolCallSkipped() {
        // name is null → skipped
        Map<String, ToolCallParser.ToolCallAccumulator> acc = Map.of(
                "0", acc("call_1", null, "{}")
        );

        var calls = parser.parseAccumulated(acc);

        assertTrue(calls.isEmpty());
    }

    @Test
    void parseAccumulated_multipleAccumulators() {
        Map<String, ToolCallParser.ToolCallAccumulator> acc = Map.of(
                "0", acc("c1", "read_file", "{}"),
                "1", acc("c2", "write_file", "{}")
        );

        var calls = parser.parseAccumulated(acc);

        assertEquals(2, calls.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void parseResponse_argumentsAsObjectInsteadOfString() {
        // Some non-standard APIs send arguments as object, not string
        String json = """
            {
              "choices": [{
                "message": {
                  "tool_calls": [{
                    "id": "c1",
                    "function": {
                      "name": "bash",
                      "arguments": {"command": "ls"}
                    }
                  }]
                }
              }]
            }
            """;

        var calls = parser.parseResponse(json);

        assertEquals(1, calls.size());
        assertEquals("bash", calls.get(0).toolName());
        assertEquals("ls", calls.get(0).args().get("command"));
    }

    @Test
    void parseFromText_argumentsFieldMissing() {
        String text = "<tool_call>{\"name\": \"bash\"}</tool_call>";

        var calls = parser.parseFromText(text);

        assertEquals(1, calls.size());
        assertEquals("bash", calls.get(0).toolName());
        assertTrue(calls.get(0).args().isEmpty());
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private static ToolCallParser.ToolCallAccumulator acc(String id, String name, String args) {
        var a = new ToolCallParser.ToolCallAccumulator();
        a.id = id;
        a.name = name;
        a.arguments.append(args);
        return a;
    }
}
