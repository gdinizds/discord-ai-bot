package dev.gdinizds.discordaibot.domain.model;

public sealed interface ReplyTarget {

    record Interaction(String interactionToken) implements ReplyTarget {}

    record Channel(String channelId, String originMessageId) implements ReplyTarget {}
}

