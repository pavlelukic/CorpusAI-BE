package com.corpusai.quiz;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.model.ModelProvider;
import com.corpusai.quiz.dto.QuizGenerationRequest;
import com.corpusai.quiz.dto.QuizResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final int DEFAULT_COUNT = 5;
    private static final String DEFAULT_LANG = "sr";
    private static final ModelProvider DEFAULT_PROVIDER = ModelProvider.OPENAI;

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/{subjectId}/generate")
    public QuizResponse generate(@PathVariable String subjectId,
                                 @Valid @RequestBody QuizGenerationRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        int count = request.count() != null ? request.count() : DEFAULT_COUNT;
        String lang = request.lang() != null ? request.lang() : DEFAULT_LANG;
        ModelProvider provider = request.provider() != null ? request.provider() : DEFAULT_PROVIDER;
        return quizService.generate(principal, subjectId, request.topic(), count, lang, provider);
    }
}
