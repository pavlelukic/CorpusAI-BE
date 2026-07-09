package com.corpusai.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllBySubjectIdOrderByUploadedAtDesc(String subjectId);

    Optional<Document> findBySubjectIdAndFileName(String subjectId, String fileName);
}
