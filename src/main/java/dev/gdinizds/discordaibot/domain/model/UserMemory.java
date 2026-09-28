package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;

public record UserMemory(long id, String guildId, String userId,
                         String content, String category, Instant updatedAt) {}

