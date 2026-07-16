package com.corpusai.flashcards;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.flashcards.dto.FlashcardResponse;
import com.corpusai.flashcards.dto.FlashcardSetResponse;
import com.corpusai.flashcards.dto.FlashcardSetSummaryResponse;
import com.corpusai.metrics.LlmFeature;
import com.corpusai.metrics.UsageRecorder;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.rag.RetrievedContent;
import com.corpusai.rag.SubjectContentRetriever;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FlashcardService {

    private final SubjectContentRetriever contentRetriever;
    private final ModelFactory modelFactory;
    private final SubjectService subjectService;
    private final SubjectAccessService subjectAccessService;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardRepository flashcardRepository;
    private final UsageRecorder usageRecorder;

    public FlashcardService(SubjectContentRetriever contentRetriever,
                            ModelFactory modelFactory,
                            SubjectService subjectService,
                            SubjectAccessService subjectAccessService,
                            FlashcardSetRepository flashcardSetRepository,
                            FlashcardRepository flashcardRepository,
                            UsageRecorder usageRecorder) {
        this.contentRetriever = contentRetriever;
        this.modelFactory = modelFactory;
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
        this.flashcardSetRepository = flashcardSetRepository;
        this.flashcardRepository = flashcardRepository;
        this.usageRecorder = usageRecorder;
    }

    @Transactional
    public FlashcardSetResponse generate(AuthenticatedUser principal, String subjectId, String topic,
                                         int count, String lang, ModelProvider provider) {
        subjectService.findById(subjectId);
        subjectAccessService.checkAccess(principal, subjectId);

        log.info("Flashcard request - subject: '{}', topic: '{}', count: {}, lang: {}, provider: {}",
                subjectId, topic, count, lang, provider);

        RetrievedContent context = contentRetriever.retrieve(subjectId, topic, count);

        log.info("Generating {} flashcard(s) from {} chunk(s)", count, context.chunkCount());

        String model = modelFactory.generationModelName(provider);
        var generator = AiServices.builder(FlashcardGenerator.class)
                .chatModel(modelFactory.chatModel(provider, model))
                .build();

        Instant startedAt = Instant.now();
        Result<GeneratedFlashcards> result = generator.generate(context.text(), count, lang);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        List<GeneratedFlashcard> generated = result.content().cards();

        TokenUsage usage = result.tokenUsage();
        log.info("Generated {} flashcard(s) - tokens in/out: {}/{}",
                generated.size(),
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null);

        // Recorded before validation: the LLM call already succeeded and cost real tokens by this
        // point, regardless of whether the response turns out usable below.
        usageRecorder.record(LlmFeature.FLASHCARDS, provider, model, usage, latencyMs,
                principal.id(), subjectId, null);

        if (generated.isEmpty()) {
            throw new IllegalStateException("Model returned no flashcards for subject: " + subjectId);
        }

        FlashcardSet set = new FlashcardSet(principal.id(), subjectId, topic, lang, provider);
        flashcardSetRepository.save(set);

        List<Flashcard> cards = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            GeneratedFlashcard g = generated.get(i);
            cards.add(new Flashcard(set.getId(), g.question(), g.answer(), g.difficulty(), g.sourceHint(), i));
        }
        flashcardRepository.saveAll(cards);

        return toSetResponse(set, cards);
    }

    public List<FlashcardSetSummaryResponse> listSets(AuthenticatedUser principal, String subjectId) {
        subjectService.findById(subjectId);
        return flashcardSetRepository.findAllByUserIdAndSubjectIdOrderByCreatedAtDesc(principal.id(), subjectId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public FlashcardSetResponse getSet(AuthenticatedUser principal, UUID setId) {
        FlashcardSet set = resolveOwnedSet(principal, setId);
        List<Flashcard> cards = flashcardRepository.findAllBySetIdOrderByPositionAsc(set.getId());
        return toSetResponse(set, cards);
    }

    public void deleteSet(AuthenticatedUser principal, UUID setId) {
        FlashcardSet set = resolveOwnedSet(principal, setId);
        flashcardSetRepository.delete(set);
    }

    private FlashcardSet resolveOwnedSet(AuthenticatedUser principal, UUID setId) {
        FlashcardSet set = flashcardSetRepository.findById(setId)
                .orElseThrow(() -> new FlashcardSetNotFoundException("Unknown flashcard set: " + setId));
        if (!set.getUserId().equals(principal.id())) {
            throw new AccessDeniedException("You do not have access to this flashcard set: " + setId);
        }
        return set;
    }

    private FlashcardSetResponse toSetResponse(FlashcardSet set, List<Flashcard> cards) {
        List<FlashcardResponse> cardResponses = cards.stream()
                .map(c -> new FlashcardResponse(c.getQuestion(), c.getAnswer(), c.getDifficulty(), c.getSourceHint()))
                .toList();
        return new FlashcardSetResponse(set.getId(), set.getSubjectId(), set.getTopic(),
                set.getLang(), set.getProvider(), set.getCreatedAt(), cardResponses);
    }

    private FlashcardSetSummaryResponse toSummaryResponse(FlashcardSet set) {
        return new FlashcardSetSummaryResponse(set.getId(), set.getSubjectId(), set.getTopic(),
                set.getLang(), set.getProvider(), set.getCreatedAt());
    }
}
