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
        Instant receivedAt,
        boolean speak) {

    public ConversationRequest {
        prompt = prompt == null ? "" : prompt;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public ConversationRequest(
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
        this(correlationId, trigger, key, guildName, username, prompt, quotedContext, attachments, target, receivedAt, false);
    }

    public boolean isEmpty() {
        return prompt.isBlank() && attachments.isEmpty();
    }
}

