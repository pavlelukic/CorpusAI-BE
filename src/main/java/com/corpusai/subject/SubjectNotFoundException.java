package com.corpusai.subject;

public class SubjectNotFoundException extends RuntimeException {

    public SubjectNotFoundException(String subjectId) {
        super("Unknown subject: " + subjectId);
    }
}