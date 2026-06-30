package ru.mcs.llmrobot.core.llm;

import ru.mcs.llmrobot.core.config.LlmProfile;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unified LLM client interface.
 * Supports both sync (ask mode) and streaming (all UI modes).
 */
public interface LlmClient {

    /**
     * Non-streaming: returns full response.
     */
    CompletableFuture<String> chat(RuntimeProfile profile, ChatRequest request);

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
