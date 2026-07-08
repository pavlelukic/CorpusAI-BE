package com.corpusai.subject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, String> {
    List<Subject> findAllByArchivedFalseOrderByCreatedAtAsc();
}
