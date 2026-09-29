CREATE TABLE ai_bot.channel_messages (
    message_id  bigint       PRIMARY KEY,
    guild_id    bigint,
    channel_id  bigint       NOT NULL,
    user_id     bigint       NOT NULL,
    username    varchar(100),
    content     text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_channel_messages_recent
    ON ai_bot.channel_messages (channel_id, created_at DESC);

CREATE INDEX ix_channel_messages_created
    ON ai_bot.channel_messages (created_at);
