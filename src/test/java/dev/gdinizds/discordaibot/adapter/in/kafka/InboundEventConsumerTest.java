package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.application.port.in.HandleConversationUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleLimitsCommandUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleMemoryCommandUseCase;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class InboundEventConsumerTest {

    private static final String BOT = "600000000000000001";
    private static final String SLASH = """
            {"eventType":"INTERACTION_COMMAND","correlationId":"0192a3b4-0000-7000-8000-00000000000%d",
             "guild":{"id":"900000000000000001","name":"HotBCT"},"channelId":"800000000000000001",
             "user":{"id":"700000000000000001","username":"guilherme"},"interactionToken":"token-%d",
             "rawPayload":{"args":{"pergunta":"oi"}}}
            """;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> handled = new CopyOnWriteArrayList<>();
    private final List<String> rejected = new CopyOnWriteArrayList<>();

    private final HandleConversationUseCase conversations = new HandleConversationUseCase() {
        @Override
        public void handle(ConversationRequest request) {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handled.add(request.correlationId());
        }

        @Override
        public void rejectBusy(ConversationRequest request) {
            rejected.add(request.correlationId());
        }
    };

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.shutdownNow();
    }

    @Test
    void shutdownWaitsForConversationsInProgress() throws Exception {
        var consumer = consumer(4);
        consumer.onInteraction(SLASH.formatted(1, 1), "ia");
        await().atMost(Duration.ofSeconds(5)).until(() -> consumer.inFlight() == 1);

        var shutdown = Thread.ofVirtual().start(consumer::destroy);
        shutdown.join(300);
        assertThat(shutdown.isAlive()).isTrue();

        release.countDown();
        shutdown.join(5000);
        assertThat(shutdown.isAlive()).isFalse();
        assertThat(handled).hasSize(1);
        assertThat(consumer.inFlight()).isZero();
    }

    @Test
    void shutdownGivesUpAfterTheDrainTimeout() throws Exception {
        var consumer = consumer(4);
        consumer.setDrainTimeout(Duration.ofMillis(200));
        consumer.onInteraction(SLASH.formatted(2, 2), "ia");
        await().atMost(Duration.ofSeconds(5)).until(() -> consumer.inFlight() == 1);

        long started = System.nanoTime();
        consumer.destroy();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void busyRejectionsAreAlsoTrackedAndReleased() {
        var consumer = consumer(1);
        consumer.onInteraction(SLASH.formatted(3, 3), "ia");
        consumer.onInteraction(SLASH.formatted(4, 4), "ia");

        await().atMost(Duration.ofSeconds(5)).until(() -> rejected.size() == 1);
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> consumer.inFlight() == 0);
        assertThat(handled).hasSize(1);
    }

    @Test
    void idleShutdownReturnsImmediately() {
        var consumer = consumer(4);

        long started = System.nanoTime();
        consumer.destroy();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    private InboundEventConsumer consumer(int maxConcurrent) {
        var bulkhead = Bulkhead.of("conversation-test", BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent).maxWaitDuration(Duration.ZERO).build());
        return new InboundEventConsumer(JsonMapper.builder().build(),
                new InboundEventMapper(BOT, Clock.systemUTC()), new TriggerResolver(BOT), conversations,
                mock(HandleMemoryCommandUseCase.class), mock(HandleLimitsCommandUseCase.class), bulkhead, executor);
    }
}
