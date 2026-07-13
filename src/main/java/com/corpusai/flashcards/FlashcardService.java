package com.corpusai.flashcards;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.flashcards.dto.FlashcardResponse;
import com.corpusai.flashcards.dto.FlashcardSetResponse;
import com.corpusai.flashcards.dto.FlashcardSetSummaryResponse;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlashcardService {

    private static final int CHUNKS_PER_CARD = 2;
    private static final int MAX_RETRIEVAL_CHUNKS = 20;
    private static final String BROAD_QUERY = "main topics key concepts overview";

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final ModelFactory modelFactory;
    private final SubjectService subjectService;
    private final SubjectAccessService subjectAccessService;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardRepository flashcardRepository;

    public FlashcardService(EmbeddingModel embeddingModel,
                            PgVectorEmbeddingStore embeddingStore,
                            ModelFactory modelFactory,
                            SubjectService subjectService,
                            SubjectAccessService subjectAccessService,
                            FlashcardSetRepository flashcardSetRepository,
                            FlashcardRepository flashcardRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
        this.flashcardSetRepository = flashcardSetRepository;
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional
    public FlashcardSetResponse generate(AuthenticatedUser principal, String subjectId, String topic,
                                         int count, String lang, ModelProvider provider) {
        subjectService.findById(subjectId);
        subjectAccessService.checkAccess(principal, subjectId);

        log.info("Flashcard request - subject: '{}', topic: '{}', count: {}, lang: {}, provider: {}",
                subjectId, topic, count, lang, provider);

        int chunkCount = Math.min(count * CHUNKS_PER_CARD, MAX_RETRIEVAL_CHUNKS);

        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(chunkCount)
                .filter(new IsEqualTo("subject_id", subjectId))
                .build();

        String query = (topic != null && !topic.isBlank()) ? topic : BROAD_QUERY;

        List<Content> chunks = retriever.retrieve(Query.from(query));
        String content = chunks.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        log.info("Generating {} flashcard(s) from {} chunk(s)", count, chunks.size());

        var generator = AiServices.builder(FlashcardGenerator.class)
                .chatModel(modelFactory.chatModel(provider, modelFor(provider)))
                .build();

        Result<GeneratedFlashcards> result = generator.generate(content, count, lang);
        List<GeneratedFlashcard> generated = result.content().cards();

        TokenUsage usage = result.tokenUsage();
        log.info("Generated {} flashcard(s) - tokens in/out: {}/{}",
                generated.size(),
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null);

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

    private String modelFor(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-4.1";
            case ANTHROPIC -> "claude-haiku-4-5";
        };
    }
}
