-- V9__performance_tuning.sql
-- 1. Partition ai_bot.channel_messages by created_at with pg_partman
ALTER TABLE ai_bot.channel_messages RENAME TO channel_messages_old;

DROP INDEX IF EXISTS ai_bot.ix_channel_messages_recent;
DROP INDEX IF EXISTS ai_bot.ix_channel_messages_created;

CREATE TABLE ai_bot.channel_messages (
    message_id  bigint       NOT NULL,
    guild_id    bigint,
    channel_id  bigint       NOT NULL,
    user_id     bigint       NOT NULL,
    username    varchar(100),
    content     text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, created_at)
) PARTITION BY RANGE (created_at);

SELECT partman.create_parent(
    p_parent_table => 'ai_bot.channel_messages',
    p_control      => 'created_at',
    p_interval     => '1 day',
    p_premake      => 3);

UPDATE partman.part_config
   SET retention = '8 days',
       retention_keep_table = false,
       retention_keep_index = false,
       infinite_time_partitions = true
 WHERE parent_table = 'ai_bot.channel_messages';

INSERT INTO ai_bot.channel_messages (message_id, guild_id, channel_id, user_id, username, content, created_at)
SELECT message_id, guild_id, channel_id, user_id, username, content, created_at
  FROM ai_bot.channel_messages_old
ON CONFLICT (message_id, created_at) DO NOTHING;

DROP TABLE ai_bot.channel_messages_old;

CREATE INDEX ix_channel_messages_recent
    ON ai_bot.channel_messages (channel_id, created_at DESC, message_id DESC);

-- 2. Covering index & fillfactor on usage_daily
CREATE INDEX ix_usage_daily_user_day
    ON ai_bot.usage_daily (user_id, day) INCLUDE (cost_micro_usd);

ALTER TABLE ai_bot.usage_daily SET (fillfactor = 85);

-- 3. Covering composite index on sent_chunks
DROP INDEX IF EXISTS ai_bot.ix_sent_chunks_lookup;
CREATE INDEX ix_sent_chunks_lookup
    ON ai_bot.sent_chunks (channel_id, content_hash, created_at);

-- 4. Composite index matching session query sort order on conversation_turns
DROP INDEX IF EXISTS ai_bot.ix_turns_session;
CREATE INDEX ix_turns_session
    ON ai_bot.conversation_turns (channel_id, user_id, created_at DESC, id DESC);

-- 5. Index for processed_events retention cleanup
CREATE INDEX IF NOT EXISTS ix_processed_events_processed_at
    ON ai_bot.processed_events (processed_at);

-- 6. Index for LRU eviction on user_memories
CREATE INDEX IF NOT EXISTS ix_user_memories_lru
    ON ai_bot.user_memories (guild_id, user_id, coalesce(last_used_at, created_at), id);

-- 7. Fillfactor for HOT updates on reminders
ALTER TABLE ai_bot.reminders SET (fillfactor = 90);
