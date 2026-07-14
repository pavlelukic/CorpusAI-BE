CREATE TABLE quizzes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id     VARCHAR(255)  NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    topic          VARCHAR(200)  NULL,
    lang           VARCHAR(2)    NOT NULL CHECK (lang IN ('en', 'sr')),
    provider       VARCHAR(20)   NOT NULL CHECK (provider IN ('OPENAI', 'ANTHROPIC')),
    question_count INT           NOT NULL,
    score          INT           NULL,
    completed_at   TIMESTAMP     NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE quiz_questions (
    id             UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id        UUID  NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    question       TEXT  NOT NULL,
    options        JSONB NOT NULL,
    correct_index  INT   NOT NULL CHECK (correct_index BETWEEN 0 AND 3),
    explanation    TEXT  NULL,
    selected_index INT   NULL CHECK (selected_index BETWEEN 0 AND 3),
    position       INT   NOT NULL
);

CREATE INDEX idx_quiz_questions_quiz_position ON quiz_questions(quiz_id, position);
