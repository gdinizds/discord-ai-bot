CREATE TABLE ai_bot.processed_events (
    correlation_id  uuid         PRIMARY KEY,
    processed_at    timestamptz  NOT NULL DEFAULT now()
);
