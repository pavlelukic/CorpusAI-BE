package com.corpusai.metrics;

import com.corpusai.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// Wraps a single ChatModel call site with usage recording. Deliberately NOT cached by
// ModelFactory: the delegate may be a shared cached instance, but each RecordingChatModel is
// constructed fresh per call site (e.g. once per subject for query compression) so the
// feature/subject label attached here can never bleed across unrelated callers of the delegate.
public class RecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final UsageRecorder usageRecorder;
    private final LlmFeature feature;
    private final ModelProvider provider;
    private final String model;
    private final String subjectId;

    public RecordingChatModel(ChatModel delegate, UsageRecorder usageRecorder, LlmFeature feature,
                               ModelProvider provider, String model, String subjectId) {
        this.delegate = delegate;
        this.usageRecorder = usageRecorder;
        this.feature = feature;
        this.provider = provider;
        this.model = model;
        this.subjectId = subjectId;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        Instant startedAt = Instant.now();
        ChatResponse response = delegate.chat(chatRequest);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        usageRecorder.record(feature, provider, model, response.tokenUsage(), latencyMs, null, subjectId, null);
        return response;
    }

    @Override
    public dev.langchain4j.model.ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}