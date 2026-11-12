-- V10__channel_messages_guild_index.sql
-- Index for server-wide (guild) message retrieval and search
CREATE INDEX IF NOT EXISTS ix_channel_messages_guild_recent
    ON ai_bot.channel_messages (guild_id, created_at DESC, message_id DESC);
