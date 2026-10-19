package dev.gdinizds.discordaibot.application.service;

import java.time.Duration;
import java.time.ZoneId;

public record ConversationSettings(
        int maxExchanges,
        int maxHistoryChars,
        int memoryTopK,
        double memoryMaxDistance,
        Duration placeholderMinDelay,
        Duration timeout,
        int attachmentsMaxCount,
        long attachmentsMaxBytes,
        long textAttachmentMaxBytes,
        ZoneId zone,
        String fallbackText,
        String busyText,
        String helpText,
        boolean ttsEnabled,
        Duration ttsTimeout,
        int ttsMaxChars) {

    public ConversationSettings(
            int maxExchanges,
            int maxHistoryChars,
            int memoryTopK,
            double memoryMaxDistance,
            Duration placeholderMinDelay,
            Duration timeout,
            int attachmentsMaxCount,
            long attachmentsMaxBytes,
            long textAttachmentMaxBytes,
            ZoneId zone,
            String fallbackText,
            String busyText,
            String helpText) {
        this(maxExchanges, maxHistoryChars, memoryTopK, memoryMaxDistance, placeholderMinDelay,
                timeout, attachmentsMaxCount, attachmentsMaxBytes, textAttachmentMaxBytes,
                zone, fallbackText, busyText, helpText, true, Duration.ofSeconds(25), 1200);
    }
}

