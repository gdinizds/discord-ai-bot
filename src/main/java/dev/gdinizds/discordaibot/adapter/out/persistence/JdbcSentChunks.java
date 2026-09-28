package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.SentChunkPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HexFormat;
import java.util.List;

public class JdbcSentChunks implements SentChunkPort {

    private static final String INSERT = """
            INSERT INTO ai_bot.sent_chunks (content_hash, channel_id, user_id, correlation_id, chunk_index)
            VALUES (:hash, :channelId, :userId, :correlationId, :index)
            """;

    private static final String EXISTS = """
            SELECT EXISTS (
                SELECT 1 FROM ai_bot.sent_chunks
                 WHERE channel_id = :channelId
                   AND content_hash = :hash
                   AND created_at > now() - interval '8 days')
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Resilience resilience;

    public JdbcSentChunks(JdbcClient jdbc, TransactionTemplate transactions, Resilience resilience) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.resilience = resilience;
    }

    @Override
    public void register(ConversationKey key, String correlationId, List<String> hashes) {
        resilience.run(Instances.DATABASE, () -> transactions.executeWithoutResult(status -> {
            for (int i = 0; i < hashes.size(); i++) {
                jdbc.sql(INSERT)
                        .param("hash", HexFormat.of().parseHex(hashes.get(i)))
                        .param("channelId", Ids.snowflake(key.channelId()))
                        .param("userId", Ids.snowflake(key.userId()))
                        .param("correlationId", Ids.uuid(correlationId))
                        .param("index", (short) i)
                        .update();
            }
        }));
    }

    @Override
    public boolean isOurs(String channelId, String hash) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql(EXISTS)
                .param("channelId", Ids.snowflake(channelId))
                .param("hash", HexFormat.of().parseHex(hash))
                .query(Boolean.class)
                .single());
    }
}

