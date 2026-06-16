package com.corpusai.ingestion;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IngestionSmokeTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private PgVectorEmbeddingStore embeddingStore;

    @Test
    void retrievedChunksAreFilteredBySubjectId() {
        var queryEmbedding = embeddingModel.embed(TextSegment.from("softver")).content();

        var procesiResults = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .filter(new IsEqualTo("subject_id", "softverski-procesi"))
                .build());

        var paterniResults = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .filter(new IsEqualTo("subject_id", "softverski-paterni"))
                .build());

        assertThat(procesiResults.matches()).isNotEmpty();
        assertThat(paterniResults.matches()).isNotEmpty();

        procesiResults.matches().forEach(match ->
                assertThat(match.embedded().metadata().getString("subject_id"))
                        .isEqualTo("softverski-procesi"));

        paterniResults.matches().forEach(match ->
                assertThat(match.embedded().metadata().getString("subject_id"))
                        .isEqualTo("softverski-paterni"));
    }
}
