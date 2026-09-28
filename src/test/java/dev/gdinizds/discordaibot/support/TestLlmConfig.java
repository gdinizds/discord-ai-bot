package dev.gdinizds.discordaibot.support;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestLlmConfig {

    @Bean
    @Primary
    public ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return new DeterministicEmbeddingModel();
    }
}

