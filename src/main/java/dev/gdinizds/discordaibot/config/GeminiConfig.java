package dev.gdinizds.discordaibot.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GeminiConfig {

    @Bean
    public ChatModel geminiChatModel(AiBotProperties properties) {
        var gemini = properties.gemini();
        return GoogleAiGeminiChatModel.builder()
                .apiKey(gemini.requireApiKey())
                .modelName(gemini.chatModel())
                .temperature(gemini.temperature())
                .maxOutputTokens(gemini.maxOutputTokens())
                .timeout(gemini.chatTimeout())
                .maxRetries(0)
                .build();
    }

    @Bean
    public EmbeddingModel geminiEmbeddingModel(AiBotProperties properties) {
        var gemini = properties.gemini();
        return GoogleAiEmbeddingModel.builder()
                .apiKey(gemini.requireApiKey())
                .modelName(gemini.embeddingModel())
                .outputDimensionality(gemini.embeddingDimensions())
                .taskType(GoogleAiEmbeddingModel.TaskType.SEMANTIC_SIMILARITY)
                .timeout(gemini.embeddingTimeout())
                .maxRetries(0)
                .build();
    }
}

