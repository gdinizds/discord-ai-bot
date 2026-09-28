package dev.gdinizds.discordaibot.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class Ids {

    private Ids() {}

    static long snowflake(String id) {
        return Long.parseLong(id);
    }

    static UUID uuid(String correlationId) {
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }
}

