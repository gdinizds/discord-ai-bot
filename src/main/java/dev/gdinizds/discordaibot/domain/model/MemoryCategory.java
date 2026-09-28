package dev.gdinizds.discordaibot.domain.model;

import java.util.Locale;
import java.util.Optional;

public enum MemoryCategory {
    FACT, PREFERENCE, SKILL, CONTEXT;

    public static Optional<MemoryCategory> parse(String value) {
        if (value == null || value.isBlank()) return Optional.of(FACT);
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

