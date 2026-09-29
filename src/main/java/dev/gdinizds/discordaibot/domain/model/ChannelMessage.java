package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;

public record ChannelMessage(
        String guildId,
        String channelId,
        String messageId,
        String userId,
        String username,
        String content,
        Instant createdAt) {}
