package com.corpusai.subject;

public class DuplicateSubjectNameException extends RuntimeException {

    public DuplicateSubjectNameException(String displayName) {
        super("A subject with a matching slug already exists for name: " + displayName);
    }
}
