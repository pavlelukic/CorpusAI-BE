package com.corpusai.config;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Value("${DB_HOST:localhost}")
    private String host;

    @Value("${DB_NAME:corpusai}")
    private String database;

    @Value("${DB_USER:postgres}")
    private String user;

    @Value("${DB_PASSWORD:postgres}")
    private String password;

    @Bean
    public PgVectorEmbeddingStore embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(5432)
                .database(database)
                .user(user)
                .password(password)
                .table("embeddings")
                .dimension(1536)
                .createTable(true)
                .build();
    }
}
