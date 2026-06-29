package com.corpusai.quiz;

import com.corpusai.quiz.dto.QuizRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/{subjectId}/generate")
    public List<Flashcard> generate(@PathVariable String subjectId,
                                    @RequestBody QuizRequest request) {
        int count = request.count() != null ? request.count() : 5;
        return quizService.generate(subjectId, request.topic(), count);
    }
}
