package com.corpusai.quiz;

public class QuizAlreadyCompletedException extends RuntimeException {
    public QuizAlreadyCompletedException(String message) {
        super(message);
    }
}
