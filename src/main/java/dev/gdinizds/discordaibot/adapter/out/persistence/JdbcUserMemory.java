package dev.gdinizds.discordaibot.adapter.out.persistence;

import com.pgvector.PGvector;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

public class JdbcUserMemory implements UserMemoryPort {

    private static final String COLUMNS = "id, guild_id, user_id, content, category, updated_at";

    private static final String SEARCH = """
            SELECT %s, embedding <=> :embedding AS distance
              FROM ai_bot.user_memories
             WHERE guild_id = :guildId AND user_id = :userId
             ORDER BY embedding <=> :embedding
             LIMIT :k
            """.formatted(COLUMNS);

    private static final String INSERT = """
            INSERT INTO ai_bot.user_memories
                   (guild_id, user_id, content, category, content_hash, embedding, embedding_model, source_correlation_id)
            VALUES (:guildId, :userId, :content, :category, :hash, :embedding, :model, :correlationId)
            ON CONFLICT (guild_id, user_id, content_hash)
            DO UPDATE SET category = EXCLUDED.category, updated_at = now()
            RETURNING id
            """;

    private static final String UPDATE = """
            UPDATE ai_bot.user_memories
               SET content = :content, category = :category, content_hash = :hash, embedding = :embedding,
                   embedding_model = :model, source_correlation_id = :correlationId, updated_at = now()
             WHERE id = :id
            """;

    private static final RowMapper<UserMemory> MEMORY = (rs, i) -> new UserMemory(
            rs.getLong("id"),
            Long.toString(rs.getLong("guild_id")),
            Long.toString(rs.getLong("user_id")),
            rs.getString("content"),
            rs.getString("category"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Resilience resilience;

    public JdbcUserMemory(JdbcClient jdbc, TransactionTemplate transactions, Resilience resilience) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.resilience = resilience;
    }

    @Override
    public List<ScoredMemory> search(String guildId, String userId, float[] embedding, int k, double maxDistance) {
        return resilience.call(Instances.DATABASE, () -> transactions.execute(status -> {
            jdbc.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();
            return jdbc.sql(SEARCH)
                    .param("embedding", new PGvector(embedding))
                    .param("guildId", Ids.snowflake(guildId))
                    .param("userId", Ids.snowflake(userId))
                    .param("k", k)
                    .query((rs, i) -> new ScoredMemory(MEMORY.mapRow(rs, i), rs.getDouble("distance")))
                    .list()
                    .stream()
                    .filter(m -> m.distance() <= maxDistance)
                    .toList();
        }));
    }

    @Override
    public long insert(NewMemory memory) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql(INSERT)
                .param("guildId", Ids.snowflake(memory.guildId()))
                .param("userId", Ids.snowflake(memory.userId()))
                .param("content", memory.content())
                .param("category", memory.category())
                .param("hash", HexFormat.of().parseHex(memory.contentHash()))
                .param("embedding", new PGvector(memory.embedding()))
                .param("model", memory.embeddingModel())
                .param("correlationId", memory.sourceCorrelationId() == null ? null : Ids.uuid(memory.sourceCorrelationId()))
                .query(Long.class)
                .single());
    }

    @Override
    public void update(long id, NewMemory memory) {
        resilience.run(Instances.DATABASE, () -> jdbc.sql(UPDATE)
                .param("id", id)
                .param("content", memory.content())
                .param("category", memory.category())
                .param("hash", HexFormat.of().parseHex(memory.contentHash()))
                .param("embedding", new PGvector(memory.embedding()))
                .param("model", memory.embeddingModel())
                .param("correlationId", memory.sourceCorrelationId() == null ? null : Ids.uuid(memory.sourceCorrelationId()))
                .update());
    }

    @Override
    public int count(String guildId, String userId) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT count(*) FROM ai_bot.user_memories WHERE guild_id = :guildId AND user_id = :userId
                        """)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .query(Integer.class)
                .single());
    }

    @Override
    public void evictLeastRecentlyUsed(String guildId, String userId) {
        resilience.run(Instances.DATABASE, () -> jdbc.sql("""
                        DELETE FROM ai_bot.user_memories
                         WHERE id = (SELECT id FROM ai_bot.user_memories
                                      WHERE guild_id = :guildId AND user_id = :userId
                                      ORDER BY coalesce(last_used_at, created_at), id
                                      LIMIT 1)
                        """)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .update());
    }

    @Override
    public List<UserMemory> list(String guildId, String userId) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT %s FROM ai_bot.user_memories
                         WHERE guild_id = :guildId AND user_id = :userId
                         ORDER BY id
                        """.formatted(COLUMNS))
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .query(MEMORY)
                .list());
    }

    @Override
    public boolean delete(String guildId, String userId, long id) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        DELETE FROM ai_bot.user_memories WHERE id = :id AND guild_id = :guildId AND user_id = :userId
                        """)
                .param("id", id)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .update()) > 0;
    }

    @Override
    public int deleteAll(String guildId, String userId) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        DELETE FROM ai_bot.user_memories WHERE guild_id = :guildId AND user_id = :userId
                        """)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .update());
    }

    @Override
    public void touch(Collection<Long> ids) {
        if (ids.isEmpty()) return;
        resilience.run(Instances.DATABASE, () -> jdbc.sql("""
                        UPDATE ai_bot.user_memories SET last_used_at = now() WHERE id IN (:ids)
                        """)
                .param("ids", List.copyOf(ids))
                .update());
    }
}

