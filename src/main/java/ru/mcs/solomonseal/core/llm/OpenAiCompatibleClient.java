package ru.mcs.solomonseal.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OpenAiCompatibleClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<String> chat(RuntimeProfile profile, ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var body = mapper.writeValueAsString(request);
                var req = buildRequest(profile, body);
                var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    throw new RuntimeException("LLM error " + resp.statusCode() + ": " + resp.body());
                }
                var root = mapper.readTree(resp.body());
                return root.path("choices").get(0).path("message").path("content").asText();
            } catch (Exception e) {
                throw new RuntimeException("chat() failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> stream(
            RuntimeProfile profile,
            ChatRequest request,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                var body = mapper.writeValueAsString(request);
                var req = buildRequest(profile, body);
                var resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    throw new RuntimeException("LLM stream error " + resp.statusCode());
                }
                try (var reader = new BufferedReader(new InputStreamReader(resp.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            var data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;
                            try {
                                var node = mapper.readTree(data);
                                var delta = node.path("choices").path(0)
                                               .path("delta").path("content");
                                if (!delta.isMissingNode() && !delta.isNull()) {
                                    onToken.accept(delta.asText());
                                }
                            } catch (Exception ignored) {
                                // malformed chunk — skip
                            }
                        }
                    }
                }
                onComplete.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private HttpRequest buildRequest(RuntimeProfile profile, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(profile.baseUrl().replaceAll("/$", "") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + profile.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}
