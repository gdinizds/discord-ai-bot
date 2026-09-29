package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.ReminderPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.Reminder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class JdbcReminders implements ReminderPort {

    private static final RowMapper<Reminder> REMINDER = (rs, i) -> new Reminder(
            rs.getLong("id"),
            Long.toString(rs.getLong("guild_id")),
            Long.toString(rs.getLong("channel_id")),
            Long.toString(rs.getLong("user_id")),
            rs.getString("content"),
            rs.getTimestamp("due_at").toInstant());

    private static final String CLAIM = """
            UPDATE ai_bot.reminders
               SET status = 'SENT', sent_at = now()
             WHERE id IN (SELECT id FROM ai_bot.reminders
                           WHERE status = 'PENDING' AND due_at <= :now
                           ORDER BY due_at
                           LIMIT :limit
                           FOR UPDATE SKIP LOCKED)
            RETURNING id, guild_id, channel_id, user_id, content, due_at
            """;

    private final JdbcClient jdbc;
    private final Resilience resilience;

    public JdbcReminders(JdbcClient jdbc, Resilience resilience) {
        this.jdbc = jdbc;
        this.resilience = resilience;
    }

    @Override
    public long create(NewReminder reminder) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        INSERT INTO ai_bot.reminders (guild_id, channel_id, user_id, content, due_at, correlation_id)
                        VALUES (:guildId, :channelId, :userId, :content, :dueAt, :correlationId)
                        RETURNING id
                        """)
                .param("guildId", Ids.snowflake(reminder.guildId()))
                .param("channelId", Ids.snowflake(reminder.channelId()))
                .param("userId", Ids.snowflake(reminder.userId()))
                .param("content", reminder.content())
                .param("dueAt", Timestamp.from(reminder.dueAt()))
                .param("correlationId", reminder.correlationId() == null ? null : Ids.uuid(reminder.correlationId()))
                .query(Long.class)
                .single());
    }

    @Override
    public int countPending(String guildId, String userId) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT count(*) FROM ai_bot.reminders
                         WHERE guild_id = :guildId AND user_id = :userId AND status = 'PENDING'
                        """)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .query(Integer.class)
                .single());
    }

    @Override
    public List<Reminder> pending(String guildId, String userId) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT id, guild_id, channel_id, user_id, content, due_at FROM ai_bot.reminders
                         WHERE guild_id = :guildId AND user_id = :userId AND status = 'PENDING'
                         ORDER BY due_at
                        """)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .query(REMINDER)
                .list());
    }

    @Override
    public boolean cancel(String guildId, String userId, long id) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        UPDATE ai_bot.reminders SET status = 'CANCELLED'
                         WHERE id = :id AND guild_id = :guildId AND user_id = :userId AND status = 'PENDING'
                        """)
                .param("id", id)
                .param("guildId", Ids.snowflake(guildId))
                .param("userId", Ids.snowflake(userId))
                .update()) > 0;
    }

    @Override
    public List<Reminder> claimDue(Instant now, int limit) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql(CLAIM)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(REMINDER)
                .list());
    }

    @Override
    public void release(long id) {
        resilience.run(Instances.DATABASE, () -> jdbc.sql("""
                        UPDATE ai_bot.reminders SET status = 'PENDING', sent_at = NULL WHERE id = :id AND status = 'SENT'
                        """)
                .param("id", id)
                .update());
    }
}
