package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.Reminder;

import java.time.Instant;
import java.util.List;

public interface ReminderPort {

    record NewReminder(String guildId, String channelId, String userId, String content, Instant dueAt,
                       String correlationId) {}

    long create(NewReminder reminder);

    int countPending(String guildId, String userId);

    List<Reminder> pending(String guildId, String userId);

    boolean cancel(String guildId, String userId, long id);

    List<Reminder> claimDue(Instant now, int limit);

    void release(long id);
}
