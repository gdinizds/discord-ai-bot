package dev.gdinizds.discordaibot.domain.model;

public record LimitsCommand(String correlationId, String guildId, String userId, String interactionToken) {}
