package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;

public record Reminder(
        long id,
        String guildId,
        String channelId,
        String userId,
        String content,
        Instant dueAt) {}
