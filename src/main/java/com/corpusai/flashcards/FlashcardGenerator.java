package com.corpusai.flashcards;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface FlashcardGenerator {

    @SystemMessage("""
            You are an expert quiz creator for University Students.
            Generate exactly {{count}} flashcards based on the provided content.
            Each flashcard must have:
                - question: a clear, specific question
                - answer: a concise but complete answer
                - difficulty: one of EASY, MEDIUM, or HARD
                - sourceHint: a brief phrase indicating the topic area (e.g. "Scrum sprints")
            Try to have an even spread of different difficulties.
            Respond only with valid JSON.
            """)
    @UserMessage("""
            Content:
            {{content}}

            Generate {{count}} flashcards from the above content.

            IMPORTANT - output language: write every question, every answer and every
            sourceHint in {{lang}} ("en" = English, "sr" = Serbian in Latin script, never
            Cyrillic). The content above is not necessarily in {{lang}}; translate as needed.
            Do not answer in the content's language unless it matches {{lang}}.
            The difficulty value must stay exactly EASY, MEDIUM, or HARD - never translate it.
            """)
    Result<GeneratedFlashcards> generate(@V("content") String content, @V("count") int count, @V("lang") String lang);
}
