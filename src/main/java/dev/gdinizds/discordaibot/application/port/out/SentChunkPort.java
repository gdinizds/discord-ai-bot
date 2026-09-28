package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ConversationKey;

import java.util.List;

public interface SentChunkPort {

    void register(ConversationKey key, String correlationId, List<String> hashes);

    boolean isOurs(String channelId, String hash);
}

