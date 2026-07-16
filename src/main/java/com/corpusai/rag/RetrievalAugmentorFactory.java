package com.corpusai.rag;

import com.corpusai.metrics.LlmFeature;
import com.corpusai.metrics.RecordingChatModel;
import com.corpusai.metrics.UsageRecorder;
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

    // Query compression is a cheap internal rewrite, so it runs on the chat tier and always on
    // OpenAI regardless of the session's provider. The model name comes from ModelFactory rather
    // than a local constant so it cannot drift from whatever the chat tier actually uses.
    private static final ModelProvider COMPRESSION_PROVIDER = ModelProvider.OPENAI;

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final ModelFactory modelFactory;
    private final UsageRecorder usageRecorder;
    private final Map<String, RetrievalAugmentor> cache = new ConcurrentHashMap<>();

    public RetrievalAugmentorFactory(EmbeddingModel embeddingModel,
                                     PgVectorEmbeddingStore embeddingStore,
                                     ModelFactory modelFactory,
                                     UsageRecorder usageRecorder) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
        this.usageRecorder = usageRecorder;
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

        String compressionModelName = modelFactory.chatModelName(COMPRESSION_PROVIDER);
        var compressionModel = new RecordingChatModel(
                modelFactory.chatModel(COMPRESSION_PROVIDER, compressionModelName),
                usageRecorder, LlmFeature.QUERY_COMPRESSION, COMPRESSION_PROVIDER, compressionModelName, subjectId);

        var queryTransformer = CompressingQueryTransformer.builder()
                .chatModel(compressionModel)
                .build();

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .queryTransformer(queryTransformer)
                .build();
    }
}
