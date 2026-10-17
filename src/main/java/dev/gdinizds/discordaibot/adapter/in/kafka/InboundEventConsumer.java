package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.application.port.in.HandleConversationUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleLimitsCommandUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleMemoryCommandUseCase;
import dev.gdinizds.discordaibot.application.port.in.RecordChannelMessageUseCase;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class InboundEventConsumer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InboundEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InboundEventMapper mapper;
    private final TriggerResolver triggerResolver;
    private final HandleConversationUseCase conversations;
    private final HandleMemoryCommandUseCase memoryCommands;
    private final HandleLimitsCommandUseCase limitsCommands;
    private final RecordChannelMessageUseCase channelLog;
    private final Bulkhead bulkhead;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ReentrantLock drainLock = new ReentrantLock();
    private final Condition drained = drainLock.newCondition();
    private Duration drainTimeout = Duration.ofSeconds(95);

    public InboundEventConsumer(ObjectMapper objectMapper, InboundEventMapper mapper,
                                TriggerResolver triggerResolver, HandleConversationUseCase conversations,
                                HandleMemoryCommandUseCase memoryCommands,
                                HandleLimitsCommandUseCase limitsCommands, RecordChannelMessageUseCase channelLog,
                                Bulkhead bulkhead, @Qualifier("conversationExecutor") ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.triggerResolver = triggerResolver;
        this.conversations = conversations;
        this.memoryCommands = memoryCommands;
        this.limitsCommands = limitsCommands;
        this.channelLog = channelLog;
        this.bulkhead = bulkhead;
        this.executor = executor;
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-interactions}", containerFactory = "kafkaListenerContainerFactory")
    public void onInteraction(@Payload String payload,
                              @Header(name = "command-name", required = false) String commandName,
                              Acknowledgment ack) {
        if ("ia".equals(commandName)) {
            parse(payload).ifPresentOrElse(
                    event -> dispatch(mapper.fromSlash(event), ack),
                    () -> ackIfPresent(ack));
        } else if ("ia-memoria".equals(commandName)) {
            parse(payload).ifPresentOrElse(event -> {
                var command = mapper.toMemoryCommand(event);
                submit(() -> {
                    try {
                        withMdc(command.correlationId(), command.guildId(), "MEMORY",
                                () -> memoryCommands.handle(command));
                    } finally {
                        ackIfPresent(ack);
                    }
                });
            }, () -> ackIfPresent(ack));
        } else if ("ia-limites".equals(commandName)) {
            parse(payload).ifPresentOrElse(event -> {
                var command = mapper.toLimitsCommand(event);
                submit(() -> {
                    try {
                        withMdc(command.correlationId(), command.guildId(), "LIMITS",
                                () -> limitsCommands.handle(command));
                    } finally {
                        ackIfPresent(ack);
                    }
                });
            }, () -> ackIfPresent(ack));
        } else {
            ackIfPresent(ack);
        }
    }

    public void onInteraction(String payload, String commandName) {
        onInteraction(payload, commandName, null);
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-commands}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCommand(@Payload String payload,
                                 @Header(name = "command-name", required = false) String commandName,
                                 Acknowledgment ack) {
        if (!"ia".equals(commandName)) {
            ackIfPresent(ack);
            return;
        }
        parse(payload).ifPresentOrElse(
                event -> dispatch(mapper.fromDotCommand(event), ack),
                () -> ackIfPresent(ack));
    }

    public void onMessageCommand(String payload, String commandName) {
        onMessageCommand(payload, commandName, null);
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-messages}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCreated(@Payload String payload, Acknowledgment ack) {
        Optional<InboundEvent> parsed = parse(payload);
        parsed.ifPresent(this::record);
        if (!triggerResolver.mayConcernBot(payload)) {
            ackIfPresent(ack);
            return;
        }
        if (parsed.isEmpty()) {
            ackIfPresent(ack);
            return;
        }
        var event = parsed.get();
        var triggerOpt = triggerResolver.resolve(event);
        if (triggerOpt.isEmpty()) {
            ackIfPresent(ack);
            return;
        }
        dispatch(mapper.fromMessage(event, triggerOpt.get()), ack);
    }

    public void onMessageCreated(String payload) {
        onMessageCreated(payload, null);
    }

    private void record(InboundEvent event) {
        try {
            channelLog.record(mapper.toChannelMessage(event));
        } catch (RuntimeException e) {
            log.debug("Channel message not recorded: {}", e.toString());
        }
    }

    private void dispatch(ConversationRequest request, Acknowledgment ack) {
        if (!bulkhead.tryAcquirePermission()) {
            submit(() -> {
                try {
                    withMdc(request, () -> conversations.rejectBusy(request));
                } finally {
                    ackIfPresent(ack);
                }
            });
            return;
        }
        try {
            submit(() -> {
                try {
                    withMdc(request, () -> conversations.handle(request));
                } finally {
                    try {
                        bulkhead.onComplete();
                    } finally {
                        ackIfPresent(ack);
                    }
                }
            });
        } catch (RuntimeException e) {
            bulkhead.onComplete();
            throw e;
        }
    }

    public void setDrainTimeout(Duration drainTimeout) {
        this.drainTimeout = drainTimeout;
    }

    int inFlight() {
        return inFlight.get();
    }

    @Override
    public void destroy() {
        long deadline = System.nanoTime() + drainTimeout.toNanos();
        drainLock.lock();
        try {
            while (inFlight.get() > 0) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    log.warn("Shutting down with {} conversation(s) still in progress", inFlight.get());
                    return;
                }
                log.info("Waiting for {} conversation(s) to finish before shutdown", inFlight.get());
                try {
                    if (!drained.await(remainingNanos, TimeUnit.NANOSECONDS)) {
                        log.warn("Shutting down with {} conversation(s) still in progress", inFlight.get());
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            drainLock.unlock();
        }
    }

    private void submit(Runnable task) {
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    release();
                }
            });
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    private void release() {
        if (inFlight.decrementAndGet() == 0) {
            drainLock.lock();
            try {
                drained.signalAll();
            } finally {
                drainLock.unlock();
            }
        }
    }

    private static void ackIfPresent(Acknowledgment ack) {
        if (ack != null) {
            try {
                ack.acknowledge();
            } catch (Exception e) {
                log.warn("Failed to acknowledge Kafka message: {}", e.getMessage());
            }
        }
    }

    private Optional<InboundEvent> parse(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, InboundEvent.class));
        } catch (RuntimeException e) {
            log.warn("Discarding malformed inbound event: {}", e.toString());
            return Optional.empty();
        }
    }

    private static void withMdc(ConversationRequest request, Runnable task) {
        withMdc(request.correlationId(), request.key().guildId(), request.trigger().name(), task);
    }

    private static void withMdc(String correlationId, String guildId, String trigger, Runnable task) {
        MDC.put("correlation_id", correlationId);
        MDC.put("guild_id", guildId);
        MDC.put("trigger", trigger);
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Unhandled failure while processing request", e);
        } finally {
            MDC.clear();
        }
    }
}

