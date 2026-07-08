CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE user_subjects (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id VARCHAR(255) NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    granted_at TIMESTAMP NOT NULL DEFAULT now(),
    granted_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, subject_id)
);
