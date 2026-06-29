package com.corpusai.quiz;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

interface QuizGenerator {

    @SystemMessage("""
            You are an expert quiz creator for University Students.
            Generate exactly {{count}} flashcards based on the provided content.
            Each flashcard must have:
                - question: a clear, specific question
                - answer: a concise but complete answer
                - difficulty: one of EASY, MEDIUM, or HARD
                - sourceHint: a brief phrase indicating the topic area (e.g. "Scrum sprints")
            Respond only with valid JSON.
            """)
    @UserMessage("""
            Content:
            {{content}}
            
            Generate {{count}} flashcards from the above content.
            """)
    List<Flashcard> generate(@V("content") String content, @V("count") int count);
}
