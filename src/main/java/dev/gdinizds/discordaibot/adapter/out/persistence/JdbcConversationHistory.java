package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.ConversationHistoryPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.Role;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcConversationHistory implements ConversationHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcConversationHistory.class);

    private static final String LAST_TURNS = """
            SELECT role, content, correlation_id, trigger_type, created_at
              FROM ai_bot.conversation_turns
             WHERE channel_id = :channelId
               AND user_id = :userId
               AND created_at > now() - interval '8 days'
             ORDER BY created_at DESC, id DESC
             LIMIT :limit
            """;

    private static final String INSERT = """
            INSERT INTO ai_bot.conversation_turns
                   (guild_id, channel_id, user_id, role, content, correlation_id, trigger_type,
                    input_tokens, output_tokens, created_at)
            VALUES (:guildId, :channelId, :userId, :role, :content, :correlationId, :trigger,
                    :inputTokens, :outputTokens, :createdAt)
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Resilience resilience;

    public JdbcConversationHistory(JdbcClient jdbc, TransactionTemplate transactions, Resilience resilience) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.resilience = resilience;
    }

    @Override
    public List<ChatTurn> lastTurns(ConversationKey key, int limit) {
        try {
            List<ChatTurn> newestFirst = resilience.call(Instances.DATABASE, () -> jdbc.sql(LAST_TURNS)
                    .param("channelId", Ids.snowflake(key.channelId()))
                    .param("userId", Ids.snowflake(key.userId()))
                    .param("limit", limit)
                    .query((rs, i) -> new ChatTurn(key,
                            Role.valueOf(rs.getString("role")),
                            rs.getString("content"),
                            rs.getString("correlation_id"),
                            TriggerType.valueOf(rs.getString("trigger_type")),
                            null,
                            rs.getTimestamp("created_at").toInstant()))
                    .list());
            List<ChatTurn> chronological = new ArrayList<>(newestFirst);
            Collections.reverse(chronological);
            return chronological;
        } catch (RuntimeException e) {
            log.warn("database: session read failed, continuing with empty session: {}", e.toString());
            return List.of();
        }
    }

    @Override
    public void append(List<ChatTurn> turns) {
        resilience.run(Instances.DATABASE, () -> transactions.executeWithoutResult(status -> {
            for (ChatTurn turn : turns) {
                jdbc.sql(INSERT)
                        .param("guildId", Ids.snowflake(turn.key().guildId()))
                        .param("channelId", Ids.snowflake(turn.key().channelId()))
                        .param("userId", Ids.snowflake(turn.key().userId()))
                        .param("role", turn.role().name())
                        .param("content", turn.content())
                        .param("correlationId", Ids.uuid(turn.correlationId()))
                        .param("trigger", turn.trigger().name())
                        .param("inputTokens", turn.usage() == null ? null : turn.usage().input())
                        .param("outputTokens", turn.usage() == null ? null : turn.usage().output())
                        .param("createdAt", Timestamp.from(turn.createdAt()))
                        .update();
            }
        }));
    }
}

