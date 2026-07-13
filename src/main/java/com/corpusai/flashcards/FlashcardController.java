package com.corpusai.flashcards;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.flashcards.dto.FlashcardRequest;
import com.corpusai.flashcards.dto.FlashcardSetResponse;
import com.corpusai.flashcards.dto.FlashcardSetSummaryResponse;
import com.corpusai.model.ModelProvider;
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

    @GetMapping
    public List<FlashcardSetSummaryResponse> listSets(@RequestParam String subjectId,
                                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return flashcardService.listSets(principal, subjectId);
    }

    @GetMapping("/{setId}")
    public FlashcardSetResponse getSet(@PathVariable UUID setId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return flashcardService.getSet(principal, setId);
    }

    @DeleteMapping("/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSet(@PathVariable UUID setId,
                          @AuthenticationPrincipal AuthenticatedUser principal) {
        flashcardService.deleteSet(principal, setId);
    }
}
