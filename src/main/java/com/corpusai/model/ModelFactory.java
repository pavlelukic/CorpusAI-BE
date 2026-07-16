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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelFactory {

    // Boxed deliberately: temperatureFor() returns null for models that reject an explicit temperature
    private static final Double DEFAULT_TEMPERATURE = 0.7;

    // Frontier models (gpt-5.6-terra, claude-sonnet-5) accept only their default temperature
    private static final Set<String> MODELS_ACCEPTING_TEMPERATURE =
            Set.of("gpt-5.4-mini", "gpt-4o-mini", "claude-haiku-4-5");

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

    // The concrete model behind each provider, kept here rather than in the feature services so
    // the provider -> model map lives in one place. The two roles are deliberately different:
    // chat streams token-by-token and favours a fast, cheap model, while flashcard/quiz generation
    // needs stronger structured-output behaviour. Both names are recorded on every llm_usage row.

    public String chatModelName(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-5.4-mini";
            case ANTHROPIC -> "claude-haiku-4-5";
        };
    }

    public String generationModelName(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-5.6-terra";
            case ANTHROPIC -> "claude-sonnet-5";
        };
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
                    .temperature(temperatureFor(modelName))
                    .strictJsonSchema(true)
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();
            // Anthropic deliberately does NOT advertise RESPONSE_FORMAT_JSON_SCHEMA. langchain4j
            // 1.10.0 emits the now-rejected `output_format` field for Anthropic's native structured
            // output, so AiServices instead appends prompt-based JSON format instructions. Structured
            // callers (e.g. FlashcardGenerator) therefore return a POJO wrapper rather than a bare
            // List, since langchain4j's collection format-instructions path is unimplemented.
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(modelName)
                    .temperature(temperatureFor(modelName))
                    .build();
        };
    }

    private StreamingChatModel buildStreamingChatModel(ModelProvider provider, String modelName) {
        return switch (provider) {
            case OPENAI -> OpenAiStreamingChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(modelName)
                    .temperature(temperatureFor(modelName))
                    .build();
            case ANTHROPIC -> AnthropicStreamingChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(modelName)
                    .temperature(temperatureFor(modelName))
                    .build();
        };
    }

    // null tells the langchain4j builders to omit the field entirely, so the model applies its own
    // default rather than us sending a value it will reject.
    private Double temperatureFor(String modelName) {
        return MODELS_ACCEPTING_TEMPERATURE.contains(modelName) ? DEFAULT_TEMPERATURE : null;
    }

    private String cacheKey(ModelProvider provider, String modelName){
        return provider.name() + ":" + modelName;
    }

}
