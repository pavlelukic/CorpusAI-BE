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
}