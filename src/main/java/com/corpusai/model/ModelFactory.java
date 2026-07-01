package com.corpusai.model;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelFactory {

    private static final double DEFAULT_TEMPERATURE = 0.7;

    private final String openAiApiKey;
    private final String anthropicApiKey;

    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingChatModels = new ConcurrentHashMap<>();

    public ModelFactory(@Value("${OPENAI_API_KEY}") String openAiApiKey,
                        @Value("${ANTHROPIC_API_KEY}") String anthropicApiKey) {
        this.openAiApiKey = openAiApiKey;
        this.anthropicApiKey = anthropicApiKey;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-3-small")
                .dimensions(1536)
                .build();
    }

    public ChatModel chatModel(ModelProvider provider, String modelName) {
        return chatModels.computeIfAbsent(cacheKey(provider, modelName),
                key -> buildChatModel(provider, modelName));
    }

    public StreamingChatModel streamingChatModel(ModelProvider provider, String modelName) {
        return streamingChatModels.computeIfAbsent(cacheKey(provider, modelName),
                key -> buildStreamingChatModel(provider, modelName));
    }

    private ChatModel buildChatModel(ModelProvider provider, String modelName) {
        return switch (provider) {
            case OPENAI -> OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(modelName)
                    .temperature(DEFAULT_TEMPERATURE)
                    .strictJsonSchema(true)
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(modelName)
                    .temperature(DEFAULT_TEMPERATURE)
                    .build();
        };
    }

    private StreamingChatModel buildStreamingChatModel(ModelProvider provider, String modelName) {
        return switch (provider) {
            case OPENAI -> OpenAiStreamingChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(modelName)
                    .temperature(DEFAULT_TEMPERATURE)
                    .build();
            case ANTHROPIC -> AnthropicStreamingChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(modelName)
                    .temperature(DEFAULT_TEMPERATURE)
                    .build();
        };
    }

    private String cacheKey(ModelProvider provider, String modelName){
        return provider.name() + ":" + modelName;
    }

}
