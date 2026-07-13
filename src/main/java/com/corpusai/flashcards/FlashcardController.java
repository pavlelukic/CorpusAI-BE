package com.corpusai.flashcards;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.flashcards.dto.FlashcardRequest;
import com.corpusai.flashcards.dto.FlashcardSetResponse;
import com.corpusai.model.ModelProvider;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {

    private static final int DEFAULT_COUNT = 5;
    private static final String DEFAULT_LANG = "sr";
    private static final ModelProvider DEFAULT_PROVIDER = ModelProvider.OPENAI;

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @PostMapping("/{subjectId}/generate")
    public FlashcardSetResponse generate(@PathVariable String subjectId,
                                         @Valid @RequestBody FlashcardRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        int count = request.count() != null ? request.count() : DEFAULT_COUNT;
        String lang = request.lang() != null ? request.lang() : DEFAULT_LANG;
        ModelProvider provider = request.provider() != null ? request.provider() : DEFAULT_PROVIDER;
        return flashcardService.generate(principal, subjectId, request.topic(), count, lang, provider);
    }
}
