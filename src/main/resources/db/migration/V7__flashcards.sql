CREATE TABLE flashcard_sets (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id VARCHAR(255)  NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    topic      VARCHAR(200)  NULL,
    lang       VARCHAR(2)    NOT NULL CHECK (lang IN ('en', 'sr')),
    provider   VARCHAR(20)   NOT NULL CHECK (provider IN ('OPENAI', 'ANTHROPIC')),
    created_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id      UUID        NOT NULL REFERENCES flashcard_sets(id) ON DELETE CASCADE,
    question    TEXT        NOT NULL,
    answer      TEXT        NOT NULL,
    difficulty  VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    source_hint TEXT        NULL,
    position    INT         NOT NULL
);

CREATE INDEX idx_flashcards_set_position ON flashcards(set_id, position);
