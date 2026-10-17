package dev.gdinizds.discordaibot.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class Ids {

    private Ids() {}

    static long snowflake(String id) {
        if (id == null || id.isBlank()) return 0L;
        try {
            return Long.parseUnsignedLong(id.strip());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static UUID uuid(String correlationId) {
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }
}

