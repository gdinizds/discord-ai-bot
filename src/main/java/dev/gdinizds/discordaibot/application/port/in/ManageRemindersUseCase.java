package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.Reminder;
import dev.gdinizds.discordaibot.domain.model.ReminderResult;

import java.util.List;

public interface ManageRemindersUseCase {

    ReminderResult schedule(String guildId, String channelId, String userId, String when, String text,
                            String correlationId);

    List<Reminder> pending(String guildId, String userId);

    boolean cancel(String guildId, String userId, long id);
}
