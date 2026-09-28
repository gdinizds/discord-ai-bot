package dev.gdinizds.discordaibot.config;

import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.AttachmentFetcherPort;
import dev.gdinizds.discordaibot.application.port.out.ConversationHistoryPort;
import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.port.out.ProcessedEventPort;
import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.application.port.out.SentChunkPort;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort;
import dev.gdinizds.discordaibot.application.service.ContextAssembler;
import dev.gdinizds.discordaibot.application.service.ConversationService;
import dev.gdinizds.discordaibot.application.service.ConversationSettings;
import dev.gdinizds.discordaibot.application.service.MemoryCommandService;
import dev.gdinizds.discordaibot.application.service.MemoryService;
import dev.gdinizds.discordaibot.application.service.MemorySettings;
import dev.gdinizds.discordaibot.application.service.Sleeper;
import dev.gdinizds.discordaibot.domain.service.MessageSplitter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;

@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ConversationSettings conversationSettings(AiBotProperties p) {
        return new ConversationSettings(
                p.session().maxExchanges(),
                p.session().maxHistoryChars(),
                p.memory().topK(),
                p.memory().maxDistance(),
                p.reply().placeholderMinDelay(),
                p.conversation().timeout(),
                p.attachments().maxCount(),
                p.attachments().maxBytes(),
                p.attachments().textMaxBytes(),
                ZoneId.of(p.conversation().zone()),
                p.messages().fallback(),
                p.messages().busy(),
                p.messages().help());
    }

    @Bean
    public MessageSplitter messageSplitter(AiBotProperties p) {
        return new MessageSplitter(p.reply().chunkLimit(), p.reply().maxChunks(), p.messages().fallback());
    }

    @Bean
    public ContextAssembler contextAssembler(ConversationSettings settings, Clock clock) {
        return new ContextAssembler(settings, clock);
    }

    @Bean
    public MemoryService memoryService(UserMemoryPort memories, EmbeddingPort embedding, MetricsPort metrics,
                                       AiBotProperties p) {
        return new MemoryService(memories, embedding, metrics, new MemorySettings(
                p.memory().maxContentChars(), p.memory().dedupDistance(), p.memory().maxPerUser()));
    }

    @Bean
    public MemoryCommandService memoryCommandService(ManageMemoryUseCase memories, ProcessedEventPort processedEvents,
                                                     ReplyPublisherPort replyPublisher, MessageSplitter splitter,
                                                     AiBotProperties p) {
        return new MemoryCommandService(memories, processedEvents, replyPublisher, splitter, p.messages().fallback());
    }

    @Bean
    public ConversationService conversationService(ProcessedEventPort processedEvents, SentChunkPort sentChunks,
                                                   ReplyPublisherPort replyPublisher, ConversationHistoryPort history,
                                                   EmbeddingPort embedding, UserMemoryPort userMemory,
                                                   AttachmentFetcherPort attachmentFetcher, LlmPort llm,
                                                   ContextAssembler contextAssembler, MessageSplitter splitter,
                                                   MetricsPort metrics, ConversationSettings settings, Clock clock,
                                                   @Qualifier("conversationExecutor") ExecutorService executor) {
        return new ConversationService(processedEvents, sentChunks, replyPublisher, history, embedding, userMemory,
                attachmentFetcher, llm, contextAssembler, splitter, metrics, settings, clock, executor, Sleeper.SYSTEM);
    }
}

