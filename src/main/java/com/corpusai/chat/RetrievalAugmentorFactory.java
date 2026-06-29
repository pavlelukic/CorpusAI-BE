package com.corpusai.chat;

import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RetrievalAugmentorFactory {

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final ModelFactory modelFactory;
    private final Map<String, RetrievalAugmentor> cache = new ConcurrentHashMap<>();

    public RetrievalAugmentorFactory(EmbeddingModel embeddingModel,
                                     PgVectorEmbeddingStore embeddingStore,
                                     ModelFactory modelFactory) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
    }

    public RetrievalAugmentor forSubject(String subjectId) {
        return cache.computeIfAbsent(subjectId, this::build);
    }

    private RetrievalAugmentor build(String subjectId) {
        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(4)
                .filter(new IsEqualTo("subject_id", subjectId))
                .build();

        var queryTransformer = CompressingQueryTransformer.builder()
                .chatModel(modelFactory.chatModel(ModelProvider.OPENAI, "gpt-4o-mini"))
                .build();

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .queryTransformer(queryTransformer)
                .build();
    }
}
