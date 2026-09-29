package dev.gdinizds.discordaibot.adapter.in.scheduling;

import dev.gdinizds.discordaibot.application.port.in.DispatchRemindersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    private final DispatchRemindersUseCase reminders;

    public ReminderDispatcher(DispatchRemindersUseCase reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${ai-bot.reminders.dispatch-interval-ms:20000}",
            initialDelayString = "${ai-bot.reminders.dispatch-initial-delay-ms:30000}")
    public void dispatch() {
        try {
            reminders.dispatchDue();
        } catch (RuntimeException e) {
            log.warn("Reminder dispatch failed: {}", e.toString());
        }
    }
}
