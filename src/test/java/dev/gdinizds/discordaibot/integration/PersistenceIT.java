package dev.gdinizds.discordaibot.integration;

import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcChannelLog;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcProcessedEvents;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcReminders;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcSentChunks;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcUsage;
import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcUserMemory;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort.NewMemory;
import dev.gdinizds.discordaibot.application.port.out.ReminderPort;
import dev.gdinizds.discordaibot.domain.model.ChannelMessage;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.Reminder;
import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.service.ContentHasher;
import dev.gdinizds.discordaibot.support.DeterministicEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceIT extends IntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired JdbcUserMemory userMemory;
    @Autowired JdbcSentChunks sentChunks;
    @Autowired JdbcProcessedEvents processedEvents;
    @Autowired JdbcUsage usage;
    @Autowired JdbcChannelLog channelLog;
    @Autowired JdbcReminders reminders;

    @Test
    void flywayAppliesAllMigrationsAndPartmanKeepsEightDays() {
        int applied = jdbc.sql("SELECT count(*) FROM ai_bot.flyway_schema_history WHERE success AND version IS NOT NULL")
                .query(Integer.class).single();
        assertThat(applied).isEqualTo(8);

        List<String> retention = jdbc.sql("""
                        SELECT retention FROM partman.part_config
                         WHERE parent_table IN ('ai_bot.conversation_turns', 'ai_bot.sent_chunks')
                        """)
                .query(String.class).list();
        assertThat(retention).containsExactly("8 days", "8 days");
    }

    @Test
    void maintenanceDropsThePartitionFromNineDaysAgo() {
        jdbc.sql("""
                SELECT partman.create_partition_time('ai_bot.conversation_turns', ARRAY[now() - interval '9 days'])
                """).query(Boolean.class).single();
        jdbc.sql("""
                INSERT INTO ai_bot.conversation_turns
                       (guild_id, channel_id, user_id, role, content, correlation_id, trigger_type, created_at)
                VALUES (1, 424242, 3, 'USER', 'antigo', gen_random_uuid(), 'DOT', now() - interval '9 days')
                """).update();
        assertThat(partitionCount()).isPositive();
        String oldPartition = partitionHolding("now() - interval '9 days'");
        assertThat(oldPartition).isNotBlank();

        jdbc.sql("CALL partman.run_maintenance_proc()").update();

        assertThat(jdbc.sql("SELECT to_regclass(:name) IS NULL").param("name", oldPartition).query(Boolean.class).single())
                .isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM ai_bot.conversation_turns WHERE channel_id = 424242")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void vectorSearchReturnsOnlyTheOwnersMemoriesOrderedByDistance() {
        String guild = "11", user = "22";
        insertMemory(guild, user, "Usa Java 25");
        insertMemory(guild, user, "Prefere respostas curtas");
        insertMemory(guild, "33", "Usa Java 25");
        insertMemory("44", user, "Usa Java 25");

        List<ScoredMemory> found = userMemory.search(guild, user, DeterministicEmbeddingModel.vectorOf("Usa Java 25"), 5, 2.0);

        assertThat(found).extracting(m -> m.memory().content()).containsExactly("Usa Java 25", "Prefere respostas curtas");
        assertThat(found).allSatisfy(m -> {
            assertThat(m.memory().guildId()).isEqualTo(guild);
            assertThat(m.memory().userId()).isEqualTo(user);
        });
        assertThat(found.getFirst().distance()).isLessThan(0.001);
        assertThat(userMemory.search(guild, user, DeterministicEmbeddingModel.vectorOf("Usa Java 25"), 5, 0.35)).hasSize(1);
    }

    @Test
    void sentChunksProveAuthorship() {
        var key = new ConversationKey("1", "555", "3");
        sentChunks.register(key, UUID.randomUUID().toString(), List.of(ContentHasher.hash("resposta do bot")));

        assertThat(sentChunks.isOurs("555", ContentHasher.hash("resposta do bot\r\n"))).isTrue();
        assertThat(sentChunks.isOurs("555", ContentHasher.hash("outra coisa"))).isFalse();
        assertThat(sentChunks.isOurs("556", ContentHasher.hash("resposta do bot"))).isFalse();
    }

    @Test
    void usageAccumulatesPerUserDayAndSumsTheMonth() {
        var day = java.time.LocalDate.of(2031, 5, 10);
        usage.add("555", day, new TokenUsage(1000, 200), 800);
        usage.add("555", day, new TokenUsage(500, 100), 400);
        usage.add("556", day, new TokenUsage(10, 10), 30);
        usage.add("555", day.plusMonths(1), new TokenUsage(10, 10), 99_999);

        assertThat(usage.userDay("555", day)).isEqualTo(new UsagePort.UserDay(2, 1200));
        assertThat(usage.userDay("555", day.minusDays(1))).isEqualTo(UsagePort.UserDay.ZERO);
        assertThat(usage.costMicroUsdBetween(day.withDayOfMonth(1), day.withDayOfMonth(1).plusMonths(1))).isEqualTo(1230);
        assertThat(usage.userCostMicroUsdBetween("555", day.withDayOfMonth(1), day.withDayOfMonth(1).plusMonths(1)))
                .isEqualTo(1200);
    }

    @Test
    void processedEventsDeduplicate() {
        String id = UUID.randomUUID().toString();
        assertThat(processedEvents.tryAcquire(id)).isTrue();
        assertThat(processedEvents.tryAcquire(id)).isFalse();
    }

    @Test
    void channelLogKeepsTheLatestMessagesOfOneChannelInOrder() {
        var base = java.time.Instant.now().minusSeconds(60);
        for (int i = 0; i < 5; i++) {
            channelLog.append(new ChannelMessage("1", "777", Long.toString(9_000 + i), "3", "ana",
                    "mensagem " + i, base.plusSeconds(i)));
        }
        channelLog.append(new ChannelMessage("1", "777", "9004", "3", "ana", "duplicada", base.plusSeconds(4)));
        channelLog.append(new ChannelMessage("1", "778", "9100", "3", "ana", "outro canal", base));
        channelLog.append(new ChannelMessage("1", "777", "9200", "3", "ana", "antiga", base.minus(java.time.Duration.ofDays(3))));

        var recent = channelLog.recent("777", base.minus(java.time.Duration.ofDays(5)), 3);

        assertThat(recent).extracting(ChannelMessage::content).containsExactly("mensagem 2", "mensagem 3", "mensagem 4");
    }

    @Test
    void remindersAreClaimedOnceEvenWithConcurrentDispatchers() throws Exception {
        var due = java.time.Instant.now().minusSeconds(5);
        for (int i = 0; i < 20; i++) {
            reminders.create(new ReminderPort.NewReminder("1", "888", "3", "lembrete " + i, due, UUID.randomUUID().toString()));
        }
        reminders.create(new ReminderPort.NewReminder("1", "888", "3", "futuro",
                java.time.Instant.now().plusSeconds(3600), null));

        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<List<Reminder>>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> reminders.claimDue(java.time.Instant.now(), 50)));
            var claimed = new java.util.ArrayList<Long>();
            for (var f : futures) f.get().stream().filter(r -> r.channelId().equals("888")).forEach(r -> claimed.add(r.id()));

            assertThat(claimed).hasSize(20).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
        assertThat(reminders.pending("1", "3")).extracting(Reminder::content).containsExactly("futuro");
    }

    @Test
    void remindersCanBeCancelledOnlyByTheirOwnerAndReleasedForRetry() {
        long mine = reminders.create(new ReminderPort.NewReminder("1", "889", "40", "meu", java.time.Instant.now().plusSeconds(600), null));

        assertThat(reminders.cancel("1", "41", mine)).isFalse();
        assertThat(reminders.countPending("1", "40")).isEqualTo(1);
        assertThat(reminders.cancel("1", "40", mine)).isTrue();
        assertThat(reminders.countPending("1", "40")).isZero();

        long retry = reminders.create(new ReminderPort.NewReminder("1", "889", "42", "retry", java.time.Instant.now().minusSeconds(1), null));
        assertThat(reminders.claimDue(java.time.Instant.now(), 10)).extracting(Reminder::id).contains(retry);
        reminders.release(retry);
        assertThat(reminders.pending("1", "42")).extracting(Reminder::id).containsExactly(retry);
    }

    private void insertMemory(String guild, String user, String content) {
        userMemory.insert(new NewMemory(guild, user, content, "FACT", ContentHasher.hash(content),
                DeterministicEmbeddingModel.vectorOf(content), "test", null));
    }

    private int partitionCount() {
        return jdbc.sql("SELECT count(*) FROM partman.show_partitions('ai_bot.conversation_turns')")
                .query(Integer.class).single();
    }

    private String partitionHolding(String timestampExpression) {
        return jdbc.sql("""
                        SELECT tableoid::regclass::text FROM ai_bot.conversation_turns
                         WHERE channel_id = 424242 AND created_at < %s + interval '1 hour'
                        """.formatted(timestampExpression))
                .query(String.class).single();
    }
}

