CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id    VARCHAR(255)  NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    file_name     VARCHAR(1000) NOT NULL,
    storage_path  VARCHAR(2000) NOT NULL,
    content_hash  VARCHAR(64)   NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'INGESTING', 'READY', 'FAILED')),
    uploaded_by   UUID          NULL REFERENCES users(id),
    uploaded_at   TIMESTAMP     NOT NULL DEFAULT now(),
    error_message TEXT          NULL,
    UNIQUE (subject_id, file_name)
);

INSERT INTO documents (subject_id, file_name, storage_path, content_hash, status, uploaded_at)
SELECT subject_id, source_file, subject_id || '/' || source_file, content_hash, 'READY', ingested_at
FROM ingestion_log;

DROP TABLE ingestion_log;
