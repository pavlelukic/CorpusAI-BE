CREATE TABLE chat_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id VARCHAR(255)  NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    title      VARCHAR(255)  NULL,
    lang       VARCHAR(2)    NOT NULL CHECK (lang IN ('en', 'sr')),
    provider   VARCHAR(20)   NOT NULL CHECK (provider IN ('OPENAI', 'ANTHROPIC')),
    created_at TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_session_created ON chat_messages(session_id, created_at);
