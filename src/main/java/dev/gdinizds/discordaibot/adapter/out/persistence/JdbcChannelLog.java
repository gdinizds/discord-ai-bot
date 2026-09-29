package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.ChannelLogPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcChannelLog implements ChannelLogPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcChannelLog.class);

    private static final String INSERT = """
            INSERT INTO ai_bot.channel_messages
                   (guild_id, channel_id, message_id, user_id, username, content, created_at)
            VALUES (:guildId, :channelId, :messageId, :userId, :username, :content, :createdAt)
            ON CONFLICT (message_id) DO NOTHING
            """;

    private static final String RECENT = """
            SELECT guild_id, channel_id, message_id, user_id, username, content, created_at
              FROM ai_bot.channel_messages
             WHERE channel_id = :channelId
               AND created_at >= :since
             ORDER BY created_at DESC, message_id DESC
             LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final Resilience resilience;
    private final Duration retention;
    private final Clock clock;

    public JdbcChannelLog(JdbcClient jdbc, Resilience resilience, Duration retention, Clock clock) {
        this.jdbc = jdbc;
        this.resilience = resilience;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    public void append(ChannelMessage message) {
        resilience.run(Instances.DATABASE, () -> jdbc.sql(INSERT)
                .param("guildId", message.guildId() == null ? null : Ids.snowflake(message.guildId()))
                .param("channelId", Ids.snowflake(message.channelId()))
                .param("messageId", Ids.snowflake(message.messageId()))
                .param("userId", Ids.snowflake(message.userId()))
                .param("username", message.username())
                .param("content", message.content())
                .param("createdAt", Timestamp.from(message.createdAt() == null ? clock.instant() : message.createdAt()))
                .update());
    }

    @Override
    public List<ChannelMessage> recent(String channelId, Instant since, int limit) {
        Instant floor = clock.instant().minus(retention);
        Instant from = since == null || since.isBefore(floor) ? floor : since;
        List<ChannelMessage> newestFirst = resilience.call(Instances.DATABASE, () -> jdbc.sql(RECENT)
                .param("channelId", Ids.snowflake(channelId))
                .param("since", Timestamp.from(from))
                .param("limit", limit)
                .query((rs, i) -> new ChannelMessage(
                        rs.getObject("guild_id") == null ? null : Long.toString(rs.getLong("guild_id")),
                        Long.toString(rs.getLong("channel_id")),
                        Long.toString(rs.getLong("message_id")),
                        Long.toString(rs.getLong("user_id")),
                        rs.getString("username"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()))
                .list());
        List<ChannelMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    @Scheduled(cron = "${ai-bot.channel-log.purge-cron:0 23 * * * *}")
    public void purge() {
        try {
            int removed = jdbc.sql("DELETE FROM ai_bot.channel_messages WHERE created_at < :cutoff")
                    .param("cutoff", Timestamp.from(clock.instant().minus(retention)))
                    .update();
            if (removed > 0) log.info("Purged {} channel messages", removed);
        } catch (RuntimeException e) {
            log.warn("database: channel_messages purge failed: {}", e.toString());
        }
    }
}
