package ru.mcs.solomonseal.core.tokens;

import ru.mcs.solomonseal.core.llm.ChatMessage;
import java.util.List;

/**
 * Rough token estimation: ~4 chars per token (GPT-family heuristic).
 */
public class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int MESSAGE_OVERHEAD = 4; // per-message overhead tokens

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    public static int estimateMessages(List<ChatMessage> messages) {
        return messages.stream()
                .mapToInt(m -> MESSAGE_OVERHEAD + estimate(m.content))  // ← m.content вместо m.content()
                .sum() + 3;
    }
}
