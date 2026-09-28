package dev.gdinizds.discordaibot.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InboundEvent(
        String eventType,
        String correlationId,
        GuildRef guild,
        String channelId,
        UserRef user,
        String interactionToken,
        String messageId,
        Integer version,
        List<String> attachments,
        JsonNode rawPayload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GuildRef(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRef(String id, String username) {}

    public String guildId() {
        return guild == null ? null : guild.id();
    }

    public String userId() {
        return user == null ? null : user.id();
    }
}

