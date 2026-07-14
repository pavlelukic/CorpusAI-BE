package com.corpusai.quiz;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.model.ModelProvider;
import com.corpusai.quiz.dto.QuizDetailResponse;
import com.corpusai.quiz.dto.QuizGenerationRequest;
import com.corpusai.quiz.dto.QuizResponse;
import com.corpusai.quiz.dto.QuizSubmissionRequest;
import com.corpusai.quiz.dto.QuizSubmissionResponse;
import com.corpusai.quiz.dto.QuizSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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

    @PostMapping("/{quizId}/submit")
    public QuizSubmissionResponse submit(@PathVariable UUID quizId,
                                         @Valid @RequestBody QuizSubmissionRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        return quizService.submit(principal, quizId, request);
    }

    @GetMapping
    public List<QuizSummaryResponse> listQuizzes(@RequestParam String subjectId,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return quizService.listQuizzes(principal, subjectId);
    }

    @GetMapping("/{quizId}")
    public QuizDetailResponse getQuiz(@PathVariable UUID quizId,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return quizService.getQuiz(principal, quizId);
    }

    @DeleteMapping("/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(@PathVariable UUID quizId,
                           @AuthenticationPrincipal AuthenticatedUser principal) {
        quizService.deleteQuiz(principal, quizId);
    }
}
