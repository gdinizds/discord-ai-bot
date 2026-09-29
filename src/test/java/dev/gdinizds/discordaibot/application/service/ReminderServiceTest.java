package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.ReminderPort;
import dev.gdinizds.discordaibot.domain.model.Reminder;
import dev.gdinizds.discordaibot.domain.model.ReminderResult;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.support.RecordingReplyPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private final FakeReminders store = new FakeReminders();
    private final RecordingReplyPublisher publisher = new RecordingReplyPublisher();
    private final ReminderService service = new ReminderService(store, publisher,
            new ReminderSettings(Duration.ofMinutes(1), Duration.ofDays(30), 3, 500, 50, SP),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void relativeTimesAreScheduledFromNow() {
        assertThat(schedule("30m").dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(schedule("2h").dueAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(schedule("daqui a 1 dia").dueAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void localDateTimesUseTheServerZone() {
        var result = schedule("2026-09-30T09:00");

        assertThat(result.status()).isEqualTo(ReminderResult.Status.SCHEDULED);
        assertThat(result.dueAt()).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
        assertThat(schedule("2026-09-30 09:00").dueAt()).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
        assertThat(schedule("2026-09-30T09:00-03:00").dueAt()).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
    }

    @Test
    void invalidRequestsAreRejectedWithAReason() {
        assertThat(schedule("amanhã cedo").status()).isEqualTo(ReminderResult.Status.INVALID_TIME);
        assertThat(schedule("30s").status()).isEqualTo(ReminderResult.Status.INVALID_TIME);
        assertThat(schedule("2026-09-29T08:59").status()).isEqualTo(ReminderResult.Status.TOO_SOON);
        assertThat(schedule("40d").status()).isEqualTo(ReminderResult.Status.TOO_FAR);
        assertThat(service.schedule("1", "2", "3", "1h", " ", null).status()).isEqualTo(ReminderResult.Status.INVALID_TEXT);
        assertThat(service.schedule("1", "2", "3", "1h", "x".repeat(501), null).status())
                .isEqualTo(ReminderResult.Status.INVALID_TEXT);
        assertThat(store.created).isEmpty();
    }

    @Test
    void pendingRemindersArePerUserLimited() {
        schedule("1h");
        schedule("2h");
        schedule("3h");

        var fourth = schedule("4h");

        assertThat(fourth.status()).isEqualTo(ReminderResult.Status.TOO_MANY);
        assertThat(store.created).hasSize(3);
    }

    @Test
    void dueRemindersAreSentToTheChannelMentioningTheOwner() {
        store.due.add(new Reminder(7, "1", "2", "3", "revisar o deploy @everyone", NOW));

        int sent = service.dispatchDue();

        assertThat(sent).isEqualTo(1);
        var call = publisher.last();
        assertThat(call.kind()).isEqualTo(RecordingReplyPublisher.Kind.DIRECT);
        assertThat(call.target()).isEqualTo(new ReplyTarget.Channel("2", null));
        assertThat(call.chunks().getFirst()).startsWith("⏰ <@3> lembrete: revisar o deploy")
                .doesNotContain("@everyone");
        assertThat(UUID.fromString(call.correlationId())).isNotNull();
    }

    @Test
    void failedDeliveryIsReleasedForTheNextRound() {
        store.due.add(new Reminder(8, "1", "2", "3", "x", NOW));
        var failing = new ReminderService(store, new RecordingReplyPublisher() {
            @Override
            public void publishDirect(ReplyTarget target, String correlationId, String content) {
                throw new IllegalStateException("kafka down");
            }
        }, new ReminderSettings(Duration.ofMinutes(1), Duration.ofDays(30), 3, 500, 50, SP), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(failing.dispatchDue()).isZero();
        assertThat(store.released).containsExactly(8L);
    }

    private ReminderResult schedule(String when) {
        return service.schedule("1", "2", "3", when, "tomar água", UUID.randomUUID().toString());
    }

    private static final class FakeReminders implements ReminderPort {
        final List<NewReminder> created = new ArrayList<>();
        final List<Reminder> due = new ArrayList<>();
        final List<Long> released = new ArrayList<>();

        @Override public long create(NewReminder reminder) {
            created.add(reminder);
            return created.size();
        }
        @Override public int countPending(String guildId, String userId) { return created.size(); }
        @Override public List<Reminder> pending(String guildId, String userId) { return List.of(); }
        @Override public boolean cancel(String guildId, String userId, long id) { return false; }
        @Override public List<Reminder> claimDue(Instant now, int limit) {
            var claimed = List.copyOf(due);
            due.clear();
            return claimed;
        }
        @Override public void release(long id) { released.add(id); }
    }
}
