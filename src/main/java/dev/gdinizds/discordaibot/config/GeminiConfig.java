package dev.gdinizds.discordaibot.config;

import dev.gdinizds.discordaibot.adapter.out.gemini.FlexGeminiHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile("!test")
public class GeminiConfig {

    @Bean
    public ChatModel geminiChatModel(AiBotProperties properties,
                                     ObjectMapper objectMapper,
                                     @Value("${ai-bot.gemini.thinking-level:low}") String thinkingLevel) {
        return chatModel(properties, properties.gemini().chatModel(), thinkingLevel, objectMapper);
    }

    @Bean
    @ConditionalOnExpression("'${ai-bot.gemini.fallback-chat-model:}' != ''")
    public FallbackChatModel geminiFallbackChatModel(AiBotProperties properties,
                                                     ObjectMapper objectMapper,
                                                     @Value("${ai-bot.gemini.fallback-chat-model}") String name,
                                                     @Value("${ai-bot.gemini.thinking-level:low}") String thinkingLevel) {
        return new FallbackChatModel(name, chatModel(properties, name, thinkingLevel, objectMapper));
    }

    private static ChatModel chatModel(AiBotProperties properties, String name, String thinkingLevel, ObjectMapper objectMapper) {
        var gemini = properties.gemini();
        var builder = GoogleAiGeminiChatModel.builder();
        if (thinkingLevel != null && !thinkingLevel.isBlank()) {
            builder.thinkingConfig(GeminiThinkingConfig.builder().thinkingLevel(thinkingLevel.strip()).build());
        }
        if (gemini.serviceTier() != null && !gemini.serviceTier().isBlank()) {
            builder.httpClientBuilder(new FlexGeminiHttpClientBuilder(gemini.serviceTier().strip(), objectMapper));
        }
        return builder
                .apiKey(gemini.requireApiKey())
                .modelName(name)
                .temperature(gemini.temperature())
                .maxOutputTokens(gemini.maxOutputTokens())
                .returnThinking(true)
                .sendThinking(true)
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

