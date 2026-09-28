package dev.gdinizds.discordaibot.domain.model;

public record TokenUsage(int input, int output) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);
}

