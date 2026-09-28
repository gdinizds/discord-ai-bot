package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort.MemoryWrite;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort.NewMemory;
import dev.gdinizds.discordaibot.domain.model.MemoryCategory;
import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import dev.gdinizds.discordaibot.domain.service.ContentHasher;
import dev.gdinizds.discordaibot.domain.service.SensitiveDataFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MemoryService implements ManageMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final UserMemoryPort memories;
    private final EmbeddingPort embedding;
    private final MetricsPort metrics;
    private final MemorySettings settings;

    public MemoryService(UserMemoryPort memories, EmbeddingPort embedding, MetricsPort metrics,
                         MemorySettings settings) {
        this.memories = memories;
        this.embedding = embedding;
        this.metrics = metrics;
        this.settings = settings;
    }

    @Override
    public MemorySaveResult save(String guildId, String userId, String content, String category,
                                 String sourceCorrelationId) {
        String text = content == null ? "" : content.strip();
        var parsedCategory = MemoryCategory.parse(category);
        if (text.isEmpty() || text.length() > settings.maxContentChars() || parsedCategory.isEmpty()) {
            metrics.memoryWrite(MemoryWrite.REJECTED);
            return MemorySaveResult.REJECTED_INVALID;
        }
        if (SensitiveDataFilter.isSensitive(text)) {
            metrics.memoryWrite(MemoryWrite.REJECTED);
            return MemorySaveResult.REJECTED_SENSITIVE;
        }

        try {
            float[] vector = embedding.embed(text);
            var memory = new NewMemory(guildId, userId, text, parsedCategory.get().name(),
                    ContentHasher.hash(text), vector, embedding.modelName(), sourceCorrelationId);

            List<ScoredMemory> nearest = memories.search(guildId, userId, vector, 1, settings.dedupDistance());
            if (!nearest.isEmpty() && nearest.getFirst().distance() < settings.dedupDistance()) {
                memories.update(nearest.getFirst().memory().id(), memory);
                metrics.memoryWrite(MemoryWrite.UPDATE);
                return MemorySaveResult.UPDATED;
            }

            if (memories.count(guildId, userId) >= settings.maxPerUser()) {
                memories.evictLeastRecentlyUsed(guildId, userId);
                metrics.memoryWrite(MemoryWrite.EVICTED);
            }
            memories.insert(memory);
            metrics.memoryWrite(MemoryWrite.INSERT);
            return MemorySaveResult.INSERTED;
        } catch (RuntimeException e) {
            log.warn("Could not save memory: {}", e.toString());
            return MemorySaveResult.FAILED;
        }
    }

    @Override
    public List<UserMemory> list(String guildId, String userId) {
        return memories.list(guildId, userId);
    }

    @Override
    public boolean forget(String guildId, String userId, long id) {
        return memories.delete(guildId, userId, id);
    }

    @Override
    public int forgetAll(String guildId, String userId) {
        return memories.deleteAll(guildId, userId);
    }
}

