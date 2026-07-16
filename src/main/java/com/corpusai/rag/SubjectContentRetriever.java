package com.corpusai.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The retrieval half of flashcard and quiz generation, shared by both so the two features cannot
 * drift apart. Chat does not use this: it retrieves through langchain4j's RetrievalAugmentor
 * inside the AiService, whereas generation needs the raw context up front to put in a prompt.
 */
@Service
public class SubjectContentRetriever {

    private static final int CHUNKS_PER_ITEM = 2;
    private static final int MAX_RETRIEVAL_CHUNKS = 20;
    private static final String BROAD_QUERY = "main topics key concepts overview";

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;

    public SubjectContentRetriever(EmbeddingModel embeddingModel, PgVectorEmbeddingStore embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Retrieves subject context to generate {@code itemCount} items from. A null topic falls back
     * to a broad query, which is what topic-less generation ("cover the whole subject") relies on.
     */
    public RetrievedContent retrieve(String subjectId, String topic, int itemCount) {
        int chunkCount = Math.min(itemCount * CHUNKS_PER_ITEM, MAX_RETRIEVAL_CHUNKS);

        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(chunkCount)
                .filter(new IsEqualTo("subject_id", subjectId))
                .build();

        String query = (topic != null && !topic.isBlank()) ? topic : BROAD_QUERY;

        List<Content> chunks = retriever.retrieve(Query.from(query));
        String text = chunks.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        return new RetrievedContent(text, chunks.size());
    }
}