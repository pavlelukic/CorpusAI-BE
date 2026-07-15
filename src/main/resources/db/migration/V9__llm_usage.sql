CREATE TABLE llm_usage (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    feature       VARCHAR(20)  NOT NULL CHECK (feature IN ('CHAT', 'FLASHCARDS', 'QUIZ', 'QUERY_COMPRESSION')),
    provider      VARCHAR(20)  NOT NULL CHECK (provider IN ('OPENAI', 'ANTHROPIC')),
    model         VARCHAR(100) NOT NULL,
    input_tokens  INT          NULL,
    output_tokens INT          NULL,
    total_tokens  INT          NULL,
    latency_ms    BIGINT       NOT NULL,
    user_id       UUID         NULL,
    subject_id    VARCHAR(255) NULL,
    session_id    UUID         NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_llm_usage_created_at ON llm_usage(created_at);