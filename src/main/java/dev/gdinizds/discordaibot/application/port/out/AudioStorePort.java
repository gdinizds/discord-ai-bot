package dev.gdinizds.discordaibot.application.port.out;

public interface AudioStorePort {
    String store(String guildId, String correlationId, byte[] audioData);
}
