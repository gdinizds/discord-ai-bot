CREATE TABLE ai_bot.conversation_turns (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    guild_id        bigint       NOT NULL,
    channel_id      bigint       NOT NULL,
    user_id         bigint       NOT NULL,
    role            varchar(9)   NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content         text         NOT NULL,
    correlation_id  uuid         NOT NULL,
    trigger_type    varchar(10)  NOT NULL,
    input_tokens    integer,
    output_tokens   integer,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX ix_turns_session
    ON ai_bot.conversation_turns (channel_id, user_id, created_at DESC);

SELECT partman.create_parent(
    p_parent_table => 'ai_bot.conversation_turns',
    p_control      => 'created_at',
    p_interval     => '1 day',
    p_premake      => 3);

UPDATE partman.part_config
   SET retention = '8 days',
       retention_keep_table = false,
       retention_keep_index = false,
       infinite_time_partitions = true
 WHERE parent_table = 'ai_bot.conversation_turns';
