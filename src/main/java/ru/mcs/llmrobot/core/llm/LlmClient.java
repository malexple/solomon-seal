package ru.mcs.llmrobot.core.llm;

import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

/**
 * Unified LLM client interface.
 * Supports both sync (ask mode) and streaming (all UI modes).
 */
public interface LlmClient {

    /**
     * Non-streaming: returns full response.
     */
    CompletableFuture<String> chat(ModelConfig config, ChatRequest request);

    /**
     * Streaming: calls onToken for each token chunk, onComplete when done, onError on failure.
     */
    CompletableFuture<Void> stream(
            ModelConfig config,
            ChatRequest request,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError
    );
}
