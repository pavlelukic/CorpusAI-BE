package com.corpusai.quiz;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.quiz.dto.QuizRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
                                    @RequestBody QuizRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        int count = request.count() != null ? request.count() : 5;
        String lang = request.lang() != null ? request.lang() : "sr";
        return quizService.generate(principal, subjectId, request.topic(), count, lang);
    }
}
