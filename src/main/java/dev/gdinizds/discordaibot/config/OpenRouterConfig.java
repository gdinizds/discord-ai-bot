package dev.gdinizds.discordaibot.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!test")
public class OpenRouterConfig {

    private static final Map<String, String> OPENROUTER_HEADERS = Map.of(
            "HTTP-Referer", "https://github.com/gdinizds/discord-ai-bot",
            "X-Title", "Discord AI Bot"
    );

    @Bean
    public ChatModel openRouterChatModel(AiBotProperties properties) {
        return chatModel(properties, properties.openrouter().chatModel());
    }

    @Bean
    @ConditionalOnExpression("'${ai-bot.openrouter.fallback-chat-model:}' != ''")
    public FallbackChatModel openRouterFallbackChatModel(AiBotProperties properties,
                                                         @Value("${ai-bot.openrouter.fallback-chat-model}") String name) {
        return new FallbackChatModel(name, chatModel(properties, name));
    }

    private static ChatModel chatModel(AiBotProperties properties, String name) {
        var openrouter = properties.openrouter();
        return OpenAiChatModel.builder()
                .baseUrl(openrouter.baseUrl())
                .apiKey(openrouter.requireApiKey())
                .modelName(name)
                .temperature(openrouter.temperature())
                .maxTokens(openrouter.maxOutputTokens())
                .timeout(openrouter.chatTimeout())
                .customHeaders(OPENROUTER_HEADERS)
                .maxRetries(0)
                .build();
    }

    @Bean
    public EmbeddingModel openRouterEmbeddingModel(AiBotProperties properties) {
        var openrouter = properties.openrouter();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(openrouter.baseUrl())
                .apiKey(openrouter.requireApiKey())
                .modelName(openrouter.embeddingModel())
                .dimensions(openrouter.embeddingDimensions())
                .timeout(openrouter.embeddingTimeout())
                .customHeaders(OPENROUTER_HEADERS)
                .maxRetries(0)
                .build();
    }
}
