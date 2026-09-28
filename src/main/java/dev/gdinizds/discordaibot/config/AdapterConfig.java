package dev.gdinizds.discordaibot.config;

import dev.gdinizds.discordaibot.adapter.in.kafka.InboundEventConsumer;
import dev.gdinizds.discordaibot.adapter.in.kafka.InboundEventMapper;
import dev.gdinizds.discordaibot.adapter.in.kafka.TriggerResolver;
import dev.gdinizds.discordaibot.adapter.out.gemini.AssistantFactory;
import dev.gdinizds.discordaibot.adapter.out.gemini.FailoverLlmAdapter;
import dev.gdinizds.discordaibot.adapter.out.gemini.GeminiEmbeddingAdapter;
import dev.gdinizds.discordaibot.adapter.out.gemini.GeminiLlmAdapter;
import dev.gdinizds.discordaibot.adapter.out.gemini.ResilientChatModel;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ToolSupport;
import dev.gdinizds.discordaibot.adapter.out.http.OpenMeteoClient;
import dev.gdinizds.discordaibot.adapter.out.http.SearxngClient;
import dev.gdinizds.discordaibot.adapter.out.http.WikipediaClient;
import dev.gdinizds.discordaibot.adapter.out.image.GarageImageStore;
import dev.gdinizds.discordaibot.adapter.out.image.SafeImageDownloader;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcConversationHistory;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcProcessedEvents;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcSentChunks;
import dev.gdinizds.discordaibot.adapter.out.persistence.JdbcUserMemory;
import dev.gdinizds.discordaibot.adapter.out.s3.GarageAttachmentFetcher;
import dev.gdinizds.discordaibot.application.port.in.HandleConversationUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleMemoryCommandUseCase;
import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

@Configuration
public class AdapterConfig {

    @Bean
    public InboundEventMapper inboundEventMapper(AiBotProperties p, Clock clock) {
        return new InboundEventMapper(p.discord().botUserId(), clock);
    }

    @Bean
    public TriggerResolver triggerResolver(AiBotProperties p) {
        return new TriggerResolver(p.discord().botUserId());
    }

    @Bean
    public InboundEventConsumer inboundEventConsumer(ObjectMapper objectMapper, InboundEventMapper mapper,
                                                     TriggerResolver triggerResolver,
                                                     HandleConversationUseCase conversations,
                                                     HandleMemoryCommandUseCase memoryCommands,
                                                     @Qualifier("conversationBulkhead") Bulkhead bulkhead,
                                                     @Qualifier("conversationExecutor") ExecutorService executor) {
        return new InboundEventConsumer(objectMapper, mapper, triggerResolver, conversations, memoryCommands,
                bulkhead, executor);
    }

    @Bean
    public JdbcConversationHistory conversationHistory(JdbcClient jdbc, TransactionTemplate tx, Resilience r) {
        return new JdbcConversationHistory(jdbc, tx, r);
    }

    @Bean
    public JdbcSentChunks sentChunks(JdbcClient jdbc, TransactionTemplate tx, Resilience r) {
        return new JdbcSentChunks(jdbc, tx, r);
    }

    @Bean
    public JdbcUserMemory userMemory(JdbcClient jdbc, TransactionTemplate tx, Resilience r) {
        return new JdbcUserMemory(jdbc, tx, r);
    }

    @Bean
    public JdbcProcessedEvents processedEvents(JdbcClient jdbc, Resilience r) {
        return new JdbcProcessedEvents(jdbc, r);
    }

    @Bean
    public GarageAttachmentFetcher attachmentFetcher(S3Client s3, Resilience r) {
        return new GarageAttachmentFetcher(s3, r);
    }

    @Bean
    public SafeImageDownloader imageDownloader(AiBotProperties p) {
        return new SafeImageDownloader(p.images().downloadTimeout(), p.images().maxBytes(), p.images().userAgent());
    }

    @Bean
    public GarageImageStore imageStore(SafeImageDownloader downloader, S3Client s3, Resilience r, AiBotProperties p) {
        return new GarageImageStore(downloader, s3, r, p.s3().endpoint(), p.s3().bucket(), p.images().keyPrefix());
    }

    @Bean
    public SearxngClient searxngClient(RestClient.Builder builder, ObjectMapper om, Resilience r, AiBotProperties p) {
        return new SearxngClient(builder, om, r, p.tools().searxngUrl(), p.tools().searxngTimeout());
    }

    @Bean
    public OpenMeteoClient openMeteoClient(RestClient.Builder builder, ObjectMapper om, Resilience r, AiBotProperties p) {
        return new OpenMeteoClient(builder, om, r, p.tools().openMeteoGeocodingUrl(), p.tools().openMeteoForecastUrl(),
                p.tools().openMeteoTimeout());
    }

    @Bean
    public WikipediaClient wikipediaClient(RestClient.Builder builder, ObjectMapper om, Resilience r, AiBotProperties p) {
        return new WikipediaClient(builder, om, r, p.tools().wikipediaUrl(), p.tools().wikipediaUserAgent(),
                p.tools().wikipediaTimeout());
    }

    @Bean
    public GeminiEmbeddingAdapter embeddingAdapter(EmbeddingModel model, Resilience r, AiBotProperties p) {
        return new GeminiEmbeddingAdapter(model, r, p.gemini().embeddingModel(), p.gemini().embeddingDimensions());
    }

    @Bean
    public ToolSupport toolSupport(MetricsPort metrics, @Qualifier("resilienceExecutor") ExecutorService executor,
                                   AiBotProperties p) {
        return new ToolSupport(metrics, executor, p.tools().maxResultChars());
    }

    @Bean
    public AssistantFactory assistantFactory(ChatModel chatModel, Resilience r, SearxngClient webSearch,
                                             OpenMeteoClient weather, WikipediaClient encyclopedia,
                                             ManageMemoryUseCase memories, GarageImageStore imageStore,
                                             ToolSupport toolSupport, AiBotProperties p) {
        return new AssistantFactory(new ResilientChatModel(chatModel, r), webSearch, weather, encyclopedia,
                memories, webSearch, imageStore, toolSupport, p);
    }

    @Bean
    public LlmPort llmAdapter(AssistantFactory assistants, ObjectProvider<FallbackChatModel> fallback, Resilience r) {
        LlmPort primary = new GeminiLlmAdapter(assistants);
        FallbackChatModel secondary = fallback.getIfAvailable();
        if (secondary == null) return primary;
        LlmPort backup = new GeminiLlmAdapter(assistants.withChatModel(
                new ResilientChatModel(secondary.model(), r, ResilientChatModel.FALLBACK_INSTANCE)));
        return new FailoverLlmAdapter(primary, backup, secondary.name());
    }
}

