package dev.gdinizds.discordaibot.domain.model;

public record MemoryCommand(String correlationId, String guildId, String userId,
                            String interactionToken, String action, String memoryId) {}

