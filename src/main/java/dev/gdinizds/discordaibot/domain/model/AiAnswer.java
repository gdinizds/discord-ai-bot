package dev.gdinizds.discordaibot.domain.model;

import java.util.List;

public record AiAnswer(String text, List<String> toolsUsed,
                       TokenUsage usage, boolean fallback) {

    public AiAnswer {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
        usage = usage == null ? TokenUsage.NONE : usage;
    }
}

