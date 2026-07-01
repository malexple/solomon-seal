package ru.mcs.solomonseal.core.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Загружает URL и возвращает текстовое содержимое.
 * HTML автоматически очищается от тегов.
 */
public class WebFetchTool implements AgentTool {

    private static final int MAX_CHARS = 12000;
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s{3,}");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override public String name() { return "web_fetch"; }
    @Override public ToolPermission permission() { return ToolPermission.EXEC; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String description() {
        return "Fetch content from a URL. HTML is stripped to plain text. Requires confirmation.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "url":       {"type": "string",  "description": "URL to fetch"},
                "raw_html":  {"type": "boolean", "description": "Return raw HTML instead of plain text (default false)"},
                "max_chars": {"type": "integer", "description": "Max characters to return (default 12000)"}
              },
              "required": ["url"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        var url = (String) args.get("url");
        if (url == null || url.isBlank())
            return CompletableFuture.completedFuture(ToolResult.fail("url is required"));

        boolean rawHtml = args.containsKey("raw_html") && (Boolean) args.get("raw_html");
        int maxChars = args.containsKey("max_chars")
                ? ((Number) args.get("max_chars")).intValue() : MAX_CHARS;

        return ctx.confirmationHandler
                .confirm(name(), "Fetch URL", url)
                .thenComposeAsync(confirmed -> {
                    if (!confirmed)
                        return CompletableFuture.completedFuture(ToolResult.fail("Cancelled by user"));

                    var req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "llm-robot/1.0")
                            .GET()
                            .build();

                    return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                            .thenApply(resp -> {
                                if (resp.statusCode() >= 400)
                                    return ToolResult.fail("HTTP " + resp.statusCode() + " for " + url);

                                var body = resp.body();
                                if (!rawHtml) {
                                    body = HTML_TAGS.matcher(body).replaceAll(" ");
                                    body = WHITESPACE.matcher(body).replaceAll("\n");
                                    body = body.trim();
                                }
                                if (body.length() > maxChars) {
                                    body = body.substring(0, maxChars) + "\n[truncated]";
                                }
                                return ToolResult.ok(body);
                            })
                            .exceptionally(e -> ToolResult.fail("Fetch error: " + e.getMessage()));
                });
    }
}