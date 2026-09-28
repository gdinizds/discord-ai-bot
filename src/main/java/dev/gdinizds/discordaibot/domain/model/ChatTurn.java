package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;

public record ChatTurn(ConversationKey key, Role role, String content,
                       String correlationId, TriggerType trigger, TokenUsage usage,
                       Instant createdAt) {}

