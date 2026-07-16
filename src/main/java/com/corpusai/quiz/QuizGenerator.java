package com.corpusai.quiz;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface QuizGenerator {

    @SystemMessage("""
            You are an expert quiz creator for University Students.
            Generate exactly {{count}} multiple-choice questions based on the provided content.
            Each question must have:
                - question: a clear, specific question
                - options: exactly 4 answer options; exactly one of them is correct and
                  the other three are plausible but wrong distractors
                - correctIndex: the index (0-3) of the correct answer within options
                - explanation: a brief explanation of why the correct answer is correct
            Vary the position of the correct answer between questions - it must not
            always sit at the same index.
            Respond only with valid JSON.
            """)
    @UserMessage("""
            Content:
            {{content}}

            Generate {{count}} multiple-choice questions from the above content.

            IMPORTANT - output language: write every question, every option and every
            explanation in {{lang}} ("en" = English, "sr" = Serbian in Latin script, never
            Cyrillic). The content above is not necessarily in {{lang}}; translate as needed.
            Do not answer in the content's language unless it matches {{lang}}.
            """)
    Result<GeneratedQuiz> generate(@V("content") String content, @V("count") int count, @V("lang") String lang);
}
