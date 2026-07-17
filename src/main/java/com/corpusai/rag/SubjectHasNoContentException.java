package com.corpusai.rag;

public class SubjectHasNoContentException extends RuntimeException {

    public SubjectHasNoContentException(String subjectId) {
        super("Subject has no content to generate from: " + subjectId);
    }
}