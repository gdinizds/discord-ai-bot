package dev.gdinizds.discordaibot.application.port.out;

public interface TtsPort {
    byte[] synthesize(String text);
}
