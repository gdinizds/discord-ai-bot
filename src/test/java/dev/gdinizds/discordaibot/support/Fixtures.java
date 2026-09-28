package dev.gdinizds.discordaibot.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Fixtures {

    public static final String BOT_USER_ID = "1000000000000000001";
    public static final String GUILD_ID = "900000000000000001";
    public static final String CHANNEL_ID = "800000000000000001";
    public static final String USER_ID = "700000000000000001";

    private Fixtures() {}

    public static String event(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/events/" + name + ".json")) {
            if (in == null) throw new IllegalArgumentException("fixture not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

