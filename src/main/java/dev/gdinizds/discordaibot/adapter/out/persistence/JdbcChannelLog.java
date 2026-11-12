package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.ChannelLogPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JdbcChannelLog implements ChannelLogPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcChannelLog.class);

    private static final String INSERT = """
            INSERT INTO ai_bot.channel_messages
                   (guild_id, channel_id, message_id, user_id, username, content, created_at)
            VALUES (:guildId, :channelId, :messageId, :userId, :username, :content, :createdAt)
            ON CONFLICT (message_id, created_at) DO NOTHING
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
    public List<ChannelMessage> search(ChannelQuery query) {
        Instant floor = clock.instant().minus(retention);
        Instant from = query.since() == null || query.since().isBefore(floor) ? floor : query.since();

        StringBuilder sql = new StringBuilder("""
                SELECT guild_id, channel_id, message_id, user_id, username, content, created_at
                  FROM ai_bot.channel_messages
                 WHERE created_at >= :since
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("since", Timestamp.from(from));

        if (query.serverWide() && query.guildId() != null && !query.guildId().isBlank()) {
            sql.append("   AND guild_id = :guildId\n");
            params.put("guildId", Ids.snowflake(query.guildId()));
        } else if (query.channelId() != null && !query.channelId().isBlank()) {
            sql.append("   AND channel_id = :channelId\n");
            params.put("channelId", Ids.snowflake(query.channelId()));
        }

        if (query.users() != null && !query.users().isEmpty()) {
            List<Long> snowflakeIds = new ArrayList<>();
            List<String> usernamePatterns = new ArrayList<>();
            for (String raw : query.users()) {
                if (raw == null) continue;
                String cleaned = raw.strip().replaceAll("^<@!?(\\d+)>$", "$1").replace("@", "").strip();
                if (cleaned.isEmpty()) continue;
                try {
                    long id = Long.parseUnsignedLong(cleaned);
                    if (id > 0) snowflakeIds.add(id);
                } catch (NumberFormatException ignored) {}
                usernamePatterns.add("%" + cleaned.toLowerCase(Locale.ROOT) + "%");
            }

            if (!snowflakeIds.isEmpty() || !usernamePatterns.isEmpty()) {
                sql.append("   AND (");
                boolean first = true;
                if (!snowflakeIds.isEmpty()) {
                    sql.append("user_id IN (:userIds)");
                    params.put("userIds", snowflakeIds);
                    first = false;
                }
                for (int i = 0; i < usernamePatterns.size(); i++) {
                    if (!first) {
                        sql.append(" OR ");
                    }
                    String paramName = "userPattern" + i;
                    sql.append("LOWER(username) LIKE :").append(paramName);
                    params.put(paramName, usernamePatterns.get(i));
                    first = false;
                }
                sql.append(")\n");
            }
        }

        if (query.query() != null && !query.query().isBlank()) {
            sql.append("   AND content ILIKE :contentPattern\n");
            params.put("contentPattern", "%" + query.query().strip() + "%");
        }

        sql.append(" ORDER BY created_at DESC, message_id DESC\n");
        sql.append(" LIMIT :limit\n");
        params.put("limit", Math.clamp(query.limit() <= 0 ? 30 : query.limit(), 1, 100));

        List<ChannelMessage> newestFirst = resilience.call(Instances.DATABASE, () -> {
            var client = jdbc.sql(sql.toString());
            for (var entry : params.entrySet()) {
                client.param(entry.getKey(), entry.getValue());
            }
            return client.query((rs, i) -> new ChannelMessage(
                    rs.getObject("guild_id") == null ? null : Long.toString(rs.getLong("guild_id")),
                    Long.toString(rs.getLong("channel_id")),
                    Long.toString(rs.getLong("message_id")),
                    Long.toString(rs.getLong("user_id")),
                    rs.getString("username"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant()))
                    .list();
        });

        List<ChannelMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public List<ChannelMessage> recent(String channelId, Instant since, int limit) {
        return search(new ChannelQuery(null, channelId, false, List.of(), null, since, limit));
    }
}

