CREATE TABLE ai_bot.sent_chunks (
    content_hash    bytea        NOT NULL,
    channel_id      bigint       NOT NULL,
    user_id         bigint       NOT NULL,
    correlation_id  uuid         NOT NULL,
    chunk_index     smallint     NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now()
) PARTITION BY RANGE (created_at);

CREATE INDEX ix_sent_chunks_lookup
    ON ai_bot.sent_chunks (channel_id, content_hash);

SELECT partman.create_parent(
    p_parent_table => 'ai_bot.sent_chunks',
    p_control      => 'created_at',
    p_interval     => '1 day',
    p_premake      => 3);

UPDATE partman.part_config
   SET retention = '8 days',
       retention_keep_table = false,
       retention_keep_index = false,
       infinite_time_partitions = true
 WHERE parent_table = 'ai_bot.sent_chunks';
