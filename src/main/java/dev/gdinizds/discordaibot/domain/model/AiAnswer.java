package dev.gdinizds.discordaibot.domain.model;

import java.util.List;

public record AiAnswer(String text, List<String> toolsUsed,
                       TokenUsage usage, boolean fallback, List<OutboundImage> images, String model) {

    public AiAnswer {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
        usage = usage == null ? TokenUsage.NONE : usage;
        images = images == null ? List.of() : List.copyOf(images);
    }

    public AiAnswer(String text, List<String> toolsUsed, TokenUsage usage, boolean fallback) {
        this(text, toolsUsed, usage, fallback, List.of(), null);
    }

    public AiAnswer(String text, List<String> toolsUsed, TokenUsage usage, boolean fallback,
                    List<OutboundImage> images) {
        this(text, toolsUsed, usage, fallback, images, null);
    }
}
