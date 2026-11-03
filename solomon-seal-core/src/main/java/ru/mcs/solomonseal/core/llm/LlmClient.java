package ru.mcs.solomonseal.core.llm;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unified LLM client interface.
 * Supports both sync (ask mode) and streaming (all UI modes).
 */
public interface LlmClient {

    /**
     * Non-streaming: returns assistant's text content only.
     */
    CompletableFuture<String> chat(RuntimeProfile profile, ChatRequest request);

    /**
     * Non-streaming: returns raw JSON response body (for agent loop/tool_calls parsing).
     */
    CompletableFuture<String> complete(RuntimeProfile profile, ChatRequest request);

    /**
     * Streaming: calls onToken for each token chunk, onComplete when done, onError on failure.
     */
    CompletableFuture<Void> stream(
            RuntimeProfile profile,
            ChatRequest request,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError
    );
}
