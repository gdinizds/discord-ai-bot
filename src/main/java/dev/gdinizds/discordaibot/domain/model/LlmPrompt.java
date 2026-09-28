package dev.gdinizds.discordaibot.domain.model;

import java.util.List;

public record LlmPrompt(String correlationId, ConversationKey key, String systemPrompt,
                        List<ChatTurn> history, String userText, List<BinaryPart> binaries) {

    public LlmPrompt {
        history = history == null ? List.of() : List.copyOf(history);
        binaries = binaries == null ? List.of() : List.copyOf(binaries);
    }
}

