package com.corpusai.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ModelFactoryTest {

    @Autowired
    private ModelFactory modelFactory;

    @Test
    void buildsAndCachesChatModelsPerProviderAndModelName() {
        var gpt4oMini = modelFactory.chatModel(ModelProvider.OPENAI, "gpt-4o-mini");
        var gpt4o = modelFactory.chatModel(ModelProvider.OPENAI, "gpt-4o");
        var claudeHaiku = modelFactory.chatModel(ModelProvider.ANTHROPIC, "claude-3-5-haiku-20241022");

        assertThat(gpt4oMini).isNotNull();
        assertThat(gpt4o).isNotNull();
        assertThat(claudeHaiku).isNotNull();
        assertThat(gpt4oMini).isNotSameAs(gpt4o);

        assertThat(modelFactory.chatModel(ModelProvider.OPENAI, "gpt-4o-mini")).isSameAs(gpt4oMini);
    }

    @Test
    void buildsStreamingChatModels() {
        var streaming = modelFactory.streamingChatModel(ModelProvider.ANTHROPIC, "claude-3-5-sonnet-20241022");
        assertThat(streaming).isNotNull();
    }

    // These names end up on every llm_usage row, so the thesis comparison is only meaningful if
    // each role keeps its own model. Chat and generation differing on OpenAI is deliberate, not a
    // leftover: pinning both here means a future "let's just share one modelFor" fails loudly.
    @Test
    void chatAndGenerationUseTheirOwnOpenAiModels() {
        assertThat(modelFactory.chatModelName(ModelProvider.OPENAI)).isEqualTo("gpt-5.4-mini");
        assertThat(modelFactory.generationModelName(ModelProvider.OPENAI)).isEqualTo("gpt-5.6-terra");
        assertThat(modelFactory.chatModelName(ModelProvider.OPENAI))
                .isNotEqualTo(modelFactory.generationModelName(ModelProvider.OPENAI));
    }

    @Test
    void chatAndGenerationUseTheirOwnAnthropicModels() {
        assertThat(modelFactory.chatModelName(ModelProvider.ANTHROPIC)).isEqualTo("claude-haiku-4-5");
        assertThat(modelFactory.generationModelName(ModelProvider.ANTHROPIC)).isEqualTo("claude-sonnet-5");
        assertThat(modelFactory.chatModelName(ModelProvider.ANTHROPIC))
                .isNotEqualTo(modelFactory.generationModelName(ModelProvider.ANTHROPIC));
    }
}