CREATE TABLE ai_bot.user_memories (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id               bigint              NOT NULL,
    user_id                bigint              NOT NULL,
    content                text                NOT NULL CHECK (char_length(content) <= 500),
    category               varchar(12)         NOT NULL DEFAULT 'FACT'
                           CHECK (category IN ('FACT', 'PREFERENCE', 'SKILL', 'CONTEXT')),
    content_hash           bytea               NOT NULL,
    embedding              public.vector(768)  NOT NULL,
    embedding_model        varchar(64)         NOT NULL,
    source_correlation_id  uuid,
    created_at             timestamptz         NOT NULL DEFAULT now(),
    updated_at             timestamptz         NOT NULL DEFAULT now(),
    last_used_at           timestamptz,
    CONSTRAINT uq_user_memories_hash UNIQUE (guild_id, user_id, content_hash)
);

CREATE INDEX ix_user_memories_owner
    ON ai_bot.user_memories (guild_id, user_id);

CREATE INDEX ix_user_memories_embedding
    ON ai_bot.user_memories USING hnsw (embedding public.vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
