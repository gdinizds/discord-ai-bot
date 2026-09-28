package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
import dev.gdinizds.discordaibot.domain.model.UserMemory;

import java.util.List;

public interface ManageMemoryUseCase {

    MemorySaveResult save(String guildId, String userId, String content, String category, String sourceCorrelationId);

    List<UserMemory> list(String guildId, String userId);

    boolean forget(String guildId, String userId, long id);

    int forgetAll(String guildId, String userId);
}

