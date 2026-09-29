package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.DispatchRemindersUseCase;
import dev.gdinizds.discordaibot.application.port.in.ManageRemindersUseCase;
import dev.gdinizds.discordaibot.application.port.out.ReminderPort;
import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.domain.model.Reminder;
import dev.gdinizds.discordaibot.domain.model.ReminderResult;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.service.MessageSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class ReminderService implements ManageRemindersUseCase, DispatchRemindersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final Pattern RELATIVE = Pattern.compile(
            "^(?:em\\s+|daqui\\s+a\\s+)?(\\d{1,5})\\s*(m|min|mins|minuto|minutos|h|hr|hrs|hora|horas|d|dia|dias)$");

    private final ReminderPort reminders;
    private final ReplyPublisherPort publisher;
    private final ReminderSettings settings;
    private final Clock clock;

    public ReminderService(ReminderPort reminders, ReplyPublisherPort publisher, ReminderSettings settings, Clock clock) {
        this.reminders = reminders;
        this.publisher = publisher;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public ReminderResult schedule(String guildId, String channelId, String userId, String when, String text,
                                   String correlationId) {
        String content = text == null ? "" : text.strip();
        if (content.isEmpty() || content.length() > settings.maxTextChars()) {
            return ReminderResult.rejected(ReminderResult.Status.INVALID_TEXT,
                    "o texto precisa ter entre 1 e " + settings.maxTextChars() + " caracteres");
        }
        Instant now = clock.instant();
        Optional<Instant> due = parseWhen(when, now);
        if (due.isEmpty()) {
            return ReminderResult.rejected(ReminderResult.Status.INVALID_TIME,
                    "use um tempo relativo (30m, 2h, 1d) ou data e hora no formato 2026-10-01T09:00");
        }
        if (due.get().isBefore(now.plus(settings.minDelay()))) {
            return ReminderResult.rejected(ReminderResult.Status.TOO_SOON,
                    "o lembrete precisa ser para daqui a pelo menos " + settings.minDelay().toMinutes() + " minuto(s)");
        }
        if (due.get().isAfter(now.plus(settings.maxAhead()))) {
            return ReminderResult.rejected(ReminderResult.Status.TOO_FAR,
                    "o limite é de " + settings.maxAhead().toDays() + " dias");
        }
        try {
            if (reminders.countPending(guildId, userId) >= settings.maxPendingPerUser()) {
                return ReminderResult.rejected(ReminderResult.Status.TOO_MANY,
                        "limite de " + settings.maxPendingPerUser() + " lembretes pendentes por pessoa");
            }
            long id = reminders.create(new ReminderPort.NewReminder(guildId, channelId, userId, content, due.get(),
                    correlationId));
            return ReminderResult.scheduled(id, due.get());
        } catch (RuntimeException e) {
            log.warn("Could not schedule reminder: {}", e.toString());
            return ReminderResult.rejected(ReminderResult.Status.FAILED, "falha ao salvar o lembrete");
        }
    }

    @Override
    public List<Reminder> pending(String guildId, String userId) {
        return reminders.pending(guildId, userId);
    }

    @Override
    public boolean cancel(String guildId, String userId, long id) {
        return reminders.cancel(guildId, userId, id);
    }

    @Override
    public int dispatchDue() {
        List<Reminder> due;
        try {
            due = reminders.claimDue(clock.instant(), settings.dispatchBatch());
        } catch (RuntimeException e) {
            log.warn("Could not claim due reminders: {}", e.toString());
            return 0;
        }
        int sent = 0;
        for (Reminder reminder : due) {
            try {
                publisher.publishDirect(new ReplyTarget.Channel(reminder.channelId(), null),
                        UUID.randomUUID().toString(), message(reminder));
                sent++;
            } catch (RuntimeException e) {
                log.warn("Reminder {} not delivered, will retry: {}", reminder.id(), e.toString());
                try {
                    reminders.release(reminder.id());
                } catch (RuntimeException releaseError) {
                    log.error("Reminder {} could not be released for retry: {}", reminder.id(), releaseError.toString());
                }
            }
        }
        if (sent > 0) log.info("Delivered {} reminder(s)", sent);
        return sent;
    }

    static String message(Reminder reminder) {
        return "⏰ <@" + reminder.userId() + "> lembrete: " + MessageSplitter.sanitize(reminder.content());
    }

    Optional<Instant> parseWhen(String when, Instant now) {
        if (when == null || when.isBlank()) return Optional.empty();
        String value = when.strip().toLowerCase(Locale.ROOT);
        var relative = RELATIVE.matcher(value);
        if (relative.matches()) {
            long amount = Long.parseLong(relative.group(1));
            return Optional.of(now.plus(unit(relative.group(2)).multipliedBy(amount)));
        }
        String iso = value.replace(' ', 't').toUpperCase(Locale.ROOT);
        try {
            return Optional.of(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDateTime.parse(iso).atZone(settings.zone()).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        return Optional.empty();
    }

    private static Duration unit(String unit) {
        return switch (unit.charAt(0)) {
            case 'm' -> Duration.ofMinutes(1);
            case 'h' -> Duration.ofHours(1);
            default -> Duration.ofDays(1);
        };
    }
}
