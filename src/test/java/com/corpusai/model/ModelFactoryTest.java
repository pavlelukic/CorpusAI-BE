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

    // Both frontier generation models reject an explicit temperature ("does not support 0.7 with
    // this model" / "`temperature` is deprecated for this model"), so we must omit it for them and
    // send it for the chat tier. Pinned because getting this wrong fails only at request time,
    // against a live API — every generation call 500s, as it did during 2.8's model upgrade.
    @Test
    void temperatureIsSentOnlyToModelsThatAcceptIt() {
        assertThat(modelFactory.temperatureFor(modelFactory.chatModelName(ModelProvider.OPENAI))).isEqualTo(0.7);
        assertThat(modelFactory.temperatureFor(modelFactory.chatModelName(ModelProvider.ANTHROPIC))).isEqualTo(0.7);

        assertThat(modelFactory.temperatureFor(modelFactory.generationModelName(ModelProvider.OPENAI))).isNull();
        assertThat(modelFactory.temperatureFor(modelFactory.generationModelName(ModelProvider.ANTHROPIC))).isNull();
    }

    @Test
    void unknownModelIsBuiltWithoutATemperature() {
        assertThat(modelFactory.temperatureFor("some-future-model-9000")).isNull();
    }
}