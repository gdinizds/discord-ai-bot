package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;
import java.util.List;

public record ConversationRequest(
        String correlationId,
        TriggerType trigger,
        ConversationKey key,
        String guildName,
        String username,
        String prompt,
        String quotedContext,
        List<InputAttachment> attachments,
        ReplyTarget target,
        Instant receivedAt) {

    public ConversationRequest {
        prompt = prompt == null ? "" : prompt;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public boolean isEmpty() {
        return prompt.isBlank() && attachments.isEmpty();
    }
}

