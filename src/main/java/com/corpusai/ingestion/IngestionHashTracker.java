package com.corpusai.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Repository
public class IngestionHashTracker {
    private final JdbcTemplate jdbc;

    public IngestionHashTracker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ingestion_log (
                    id           SERIAL PRIMARY KEY,
                    subject_id   VARCHAR(255)  NOT NULL,
                    source_file  VARCHAR(1000) NOT NULL,
                    content_hash VARCHAR(64)   NOT NULL,
                    ingested_at  TIMESTAMP DEFAULT now(),
                    UNIQUE (subject_id, source_file)
                )
                """);
    }

    public boolean alreadyIngested(String subjectId, String sourceFile, String contentHash) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingestion_log WHERE subject_id = ? AND source_file = ? AND content_hash = ?",
                Integer.class, subjectId, sourceFile, contentHash);
        return count != null && count > 0;
    }

    public void recordIngestion(String subjectId, String sourceFile, String contentHash) {
        jdbc.update("""
                INSERT INTO ingestion_log (subject_id, source_file, content_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (subject_id, source_file)
                DO UPDATE SET content_hash = EXCLUDED.content_hash, ingested_at = now()
                """, subjectId, sourceFile, contentHash);
    }

    public static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
