package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.model.UserMemory;

import java.util.Collection;
import java.util.List;

public interface UserMemoryPort {

    List<ScoredMemory> search(String guildId, String userId, float[] embedding, int k, double maxDistance);

    long insert(NewMemory memory);

    void update(long id, NewMemory memory);

    int count(String guildId, String userId);

    void evictLeastRecentlyUsed(String guildId, String userId);

    List<UserMemory> list(String guildId, String userId);

    boolean delete(String guildId, String userId, long id);

    int deleteAll(String guildId, String userId);

    void touch(Collection<Long> ids);

    record NewMemory(String guildId, String userId, String content, String category, String contentHash,
                     float[] embedding, String embeddingModel, String sourceCorrelationId) {}
}

