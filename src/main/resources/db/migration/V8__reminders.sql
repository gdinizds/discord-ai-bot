CREATE TABLE ai_bot.reminders (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id        bigint       NOT NULL,
    channel_id      bigint       NOT NULL,
    user_id         bigint       NOT NULL,
    content         text         NOT NULL CHECK (char_length(content) <= 500),
    due_at          timestamptz  NOT NULL,
    status          varchar(10)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'CANCELLED')),
    correlation_id  uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz
);

CREATE INDEX ix_reminders_due
    ON ai_bot.reminders (due_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_reminders_owner
    ON ai_bot.reminders (guild_id, user_id)
    WHERE status = 'PENDING';
