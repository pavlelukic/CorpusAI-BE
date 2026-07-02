package com.corpusai.quiz;

import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuizService {

    private static final int CHUNKS_PER_CARD = 2;
    private static final int MAX_RETRIEVAL_CHUNKS = 20;
    private static final String BROAD_QUERY = "main topics key concepts overview";

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final ModelFactory modelFactory;

    public QuizService(EmbeddingModel embeddingModel,
                       PgVectorEmbeddingStore embeddingStore,
                       ModelFactory modelFactory) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
    }

    public List<Flashcard> generate(String subjectId, String topic, int count) {
        log.info("Quiz request - subject: '{}', topic: '{}', count: {}", subjectId, topic, count);

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

        var generator = AiServices.builder(QuizGenerator.class)
                .chatModel(modelFactory.chatModel(ModelProvider.OPENAI, "gpt-4.1"))
                .build();

        return generator.generate(content, count);
    }
}
