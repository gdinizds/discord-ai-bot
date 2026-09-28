package dev.gdinizds.discordaibot.support;

import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort;
import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.model.UserMemory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUserMemory implements UserMemoryPort {

    public record Row(long id, NewMemory memory, Instant lastUsedAt, Instant createdAt) {}

    private final Map<Long, Row> rows = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicLong ticks = new AtomicLong();
    public final List<Long> evicted = new ArrayList<>();

    @Override
    public List<ScoredMemory> search(String guildId, String userId, float[] embedding, int k, double maxDistance) {
        return owned(guildId, userId)
                .map(r -> new ScoredMemory(toMemory(r), cosineDistance(embedding, r.memory().embedding())))
                .filter(m -> m.distance() <= maxDistance)
                .sorted(Comparator.comparingDouble(ScoredMemory::distance))
                .limit(k)
                .toList();
    }

    @Override
    public long insert(NewMemory memory) {
        long id = ids.incrementAndGet();
        rows.put(id, new Row(id, memory, null, tick()));
        return id;
    }

    @Override
    public void update(long id, NewMemory memory) {
        rows.computeIfPresent(id, (k, r) -> new Row(id, memory, r.lastUsedAt(), r.createdAt()));
    }

    @Override
    public int count(String guildId, String userId) {
        return (int) owned(guildId, userId).count();
    }

    @Override
    public void evictLeastRecentlyUsed(String guildId, String userId) {
        owned(guildId, userId)
                .min(Comparator.comparing((Row r) -> r.lastUsedAt() == null ? r.createdAt() : r.lastUsedAt())
                        .thenComparingLong(Row::id))
                .ifPresent(r -> {
                    rows.remove(r.id());
                    evicted.add(r.id());
                });
    }

    @Override
    public List<UserMemory> list(String guildId, String userId) {
        return owned(guildId, userId).sorted(Comparator.comparingLong(Row::id)).map(InMemoryUserMemory::toMemory).toList();
    }

    @Override
    public boolean delete(String guildId, String userId, long id) {
        Row row = rows.get(id);
        if (row == null || !row.memory().guildId().equals(guildId) || !row.memory().userId().equals(userId)) return false;
        return rows.remove(id) != null;
    }

    @Override
    public int deleteAll(String guildId, String userId) {
        List<Long> owned = owned(guildId, userId).map(Row::id).toList();
        owned.forEach(rows::remove);
        return owned.size();
    }

    @Override
    public void touch(Collection<Long> ids) {
        Instant now = tick();
        ids.forEach(id -> rows.computeIfPresent(id, (k, r) -> new Row(r.id(), r.memory(), now, r.createdAt())));
    }

    public Row row(long id) {
        return rows.get(id);
    }

    private Instant tick() {
        return Instant.EPOCH.plusSeconds(ticks.incrementAndGet());
    }

    private java.util.stream.Stream<Row> owned(String guildId, String userId) {
        return rows.values().stream()
                .filter(r -> r.memory().guildId().equals(guildId) && r.memory().userId().equals(userId));
    }

    private static UserMemory toMemory(Row r) {
        return new UserMemory(r.id(), r.memory().guildId(), r.memory().userId(), r.memory().content(),
                r.memory().category(), r.createdAt());
    }

    public static double cosineDistance(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return 1 - dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

