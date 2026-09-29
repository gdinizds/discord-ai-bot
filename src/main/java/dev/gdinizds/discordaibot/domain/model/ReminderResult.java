package dev.gdinizds.discordaibot.domain.model;

import java.time.Instant;

public record ReminderResult(Status status, Long id, Instant dueAt, String detail) {

    public enum Status { SCHEDULED, INVALID_TIME, TOO_SOON, TOO_FAR, INVALID_TEXT, TOO_MANY, FAILED }

    public static ReminderResult scheduled(long id, Instant dueAt) {
        return new ReminderResult(Status.SCHEDULED, id, dueAt, null);
    }

    public static ReminderResult rejected(Status status, String detail) {
        return new ReminderResult(status, null, null, detail);
    }
}
