package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.application.port.in.HandleConversationUseCase;
import dev.gdinizds.discordaibot.application.port.in.HandleMemoryCommandUseCase;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class InboundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InboundEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InboundEventMapper mapper;
    private final TriggerResolver triggerResolver;
    private final HandleConversationUseCase conversations;
    private final HandleMemoryCommandUseCase memoryCommands;
    private final Bulkhead bulkhead;
    private final ExecutorService executor;

    public InboundEventConsumer(ObjectMapper objectMapper, InboundEventMapper mapper,
                                TriggerResolver triggerResolver, HandleConversationUseCase conversations,
                                HandleMemoryCommandUseCase memoryCommands, Bulkhead bulkhead,
                                @Qualifier("conversationExecutor") ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.triggerResolver = triggerResolver;
        this.conversations = conversations;
        this.memoryCommands = memoryCommands;
        this.bulkhead = bulkhead;
        this.executor = executor;
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-interactions}", containerFactory = "kafkaListenerContainerFactory")
    public void onInteraction(@Payload String payload,
                              @Header(name = "command-name", required = false) String commandName) {
        if ("ia".equals(commandName)) {
            parse(payload).ifPresent(event -> dispatch(mapper.fromSlash(event)));
        } else if ("ia-memoria".equals(commandName)) {
            parse(payload).ifPresent(event -> {
                var command = mapper.toMemoryCommand(event);
                executor.execute(() -> withMdc(command.correlationId(), command.guildId(), "MEMORY",
                        () -> memoryCommands.handle(command)));
            });
        }
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-commands}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCommand(@Payload String payload,
                                 @Header(name = "command-name", required = false) String commandName) {
        if (!"ia".equals(commandName)) return;
        parse(payload).ifPresent(event -> dispatch(mapper.fromDotCommand(event)));
    }

    @KafkaListener(topics = "${ai-bot.topics.inbound-messages}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCreated(@Payload String payload) {
        if (!triggerResolver.mayConcernBot(payload)) return;
        parse(payload).ifPresent(event -> triggerResolver.resolve(event)
                .ifPresent(trigger -> dispatch(mapper.fromMessage(event, trigger))));
    }

    private void dispatch(ConversationRequest request) {
        if (!bulkhead.tryAcquirePermission()) {
            executor.execute(() -> withMdc(request, () -> conversations.rejectBusy(request)));
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    withMdc(request, () -> conversations.handle(request));
                } finally {
                    bulkhead.onComplete();
                }
            });
        } catch (RuntimeException e) {
            bulkhead.onComplete();
            throw e;
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

