package dev.gdinizds.discordaibot.application.service;

import java.time.Duration;
import java.time.ZoneId;

public record ReminderSettings(
        Duration minDelay,
        Duration maxAhead,
        int maxPendingPerUser,
        int maxTextChars,
        int dispatchBatch,
        ZoneId zone) {}
