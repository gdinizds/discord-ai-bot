CREATE TABLE ai_bot.usage_daily (
    day             date         NOT NULL,
    user_id         bigint       NOT NULL,
    requests        integer      NOT NULL DEFAULT 0,
    input_tokens    bigint       NOT NULL DEFAULT 0,
    output_tokens   bigint       NOT NULL DEFAULT 0,
    cost_micro_usd  bigint       NOT NULL DEFAULT 0,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (day, user_id)
);
