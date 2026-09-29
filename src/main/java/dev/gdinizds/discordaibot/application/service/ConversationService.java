package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.HandleConversationUseCase;
import dev.gdinizds.discordaibot.application.port.out.AttachmentFetcherPort;
import dev.gdinizds.discordaibot.application.port.out.ConversationHistoryPort;
import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort.Outcome;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort.Stage;
import dev.gdinizds.discordaibot.application.port.out.ProcessedEventPort;
import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.application.port.out.SentChunkPort;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import dev.gdinizds.discordaibot.domain.model.InputAttachment;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.MediaKind;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.model.Role;
import dev.gdinizds.discordaibot.domain.model.ScoredMemory;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import dev.gdinizds.discordaibot.domain.service.ContentHasher;
import dev.gdinizds.discordaibot.domain.service.MessageSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConversationService implements HandleConversationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ProcessedEventPort processedEvents;
    private final SentChunkPort sentChunks;
    private final ReplyPublisherPort replyPublisher;
    private final ConversationHistoryPort history;
    private final EmbeddingPort embedding;
    private final UserMemoryPort userMemory;
    private final AttachmentFetcherPort attachmentFetcher;
    private final LlmPort llm;
    private final ContextAssembler contextAssembler;
    private final MessageSplitter splitter;
    private final MetricsPort metrics;
    private final ConversationSettings settings;
    private final Clock clock;
    private final ExecutorService executor;
    private final Sleeper sleeper;
    private final UsageService usage;

    public ConversationService(ProcessedEventPort processedEvents, SentChunkPort sentChunks,
                               ReplyPublisherPort replyPublisher, ConversationHistoryPort history,
                               EmbeddingPort embedding, UserMemoryPort userMemory,
                               AttachmentFetcherPort attachmentFetcher, LlmPort llm,
                               ContextAssembler contextAssembler, MessageSplitter splitter,
                               MetricsPort metrics, ConversationSettings settings, Clock clock,
                               ExecutorService executor, Sleeper sleeper, UsageService usage) {
        this.processedEvents = processedEvents;
        this.sentChunks = sentChunks;
        this.replyPublisher = replyPublisher;
        this.history = history;
        this.embedding = embedding;
        this.userMemory = userMemory;
        this.attachmentFetcher = attachmentFetcher;
        this.llm = llm;
        this.contextAssembler = contextAssembler;
        this.splitter = splitter;
        this.metrics = metrics;
        this.settings = settings;
        this.clock = clock;
        this.executor = executor;
        this.sleeper = sleeper;
        this.usage = usage;
    }

    @Override
    public void handle(ConversationRequest request) {
        long started = System.nanoTime();
        if (!shouldAnswer(request)) return;

        ReplyTarget target = request.target();
        UsageService.Decision decision = usage.check(request.key().userId());
        if (decision != UsageService.Decision.ALLOWED) {
            replyPublisher.publishDirect(target, request.correlationId(), usage.message(decision));
            metrics.request(request.trigger(), Outcome.LIMITED);
            log.info("Conversation limited: trigger={} reason={}", request.trigger(), decision);
            return;
        }

        Instant placeholderAt = null;
        if (target instanceof ReplyTarget.Channel) {
            replyPublisher.placeholder(target, request.correlationId());
            placeholderAt = clock.instant();
        }

        AiAnswer answer = generateWithinBudget(request);
        List<String> chunks = answer.fallback()
                ? List.of(settings.fallbackText())
                : splitter.split(answer.text());

        waitForPlaceholder(placeholderAt);
        long publishStarted = System.nanoTime();
        try {
            replyPublisher.publish(target, request.correlationId(), chunks,
                    answer.fallback() ? List.of() : answer.images());
        } catch (RuntimeException e) {
            log.error("Failed to publish answer: {}", e.toString());
            metrics.request(request.trigger(), Outcome.FALLBACK);
            return;
        }
        metrics.latency(Stage.PUBLISH, Duration.ofNanos(System.nanoTime() - publishStarted));

        if (!answer.fallback()) persist(request, answer, chunks);

        Duration total = Duration.ofNanos(System.nanoTime() - started);
        Outcome outcome = answer.fallback() ? Outcome.FALLBACK : Outcome.ANSWERED;
        metrics.request(request.trigger(), outcome);
        metrics.latency(Stage.TOTAL, total);
        metrics.tokens(answer.usage());
        metrics.replyChunks(chunks.size());
        log.info("Conversation finished: trigger={} outcome={} latencyMs={} inputTokens={} outputTokens={} tools={} chunks={}",
                request.trigger(), outcome, total.toMillis(), answer.usage().input(), answer.usage().output(),
                answer.toolsUsed(), chunks.size());
    }

    @Override
    public void rejectBusy(ConversationRequest request) {
        if (!shouldAnswer(request)) return;
        replyPublisher.publishDirect(request.target(), request.correlationId(), settings.busyText());
        metrics.request(request.trigger(), Outcome.FALLBACK);
        log.warn("Conversation rejected, bulkhead full: trigger={}", request.trigger());
    }

    private boolean shouldAnswer(ConversationRequest request) {
        if (!processedEvents.tryAcquire(request.correlationId())) {
            log.debug("Duplicate event ignored");
            metrics.request(request.trigger(), Outcome.DUPLICATE);
            return false;
        }
        if (request.trigger() == TriggerType.REPLY && !isReplyToUs(request)) {
            log.debug("Reply to a message not sent by discord-ai-bot ignored");
            metrics.request(request.trigger(), Outcome.IGNORED);
            return false;
        }
        if (request.isEmpty()) {
            replyPublisher.publishDirect(request.target(), request.correlationId(), settings.helpText());
            metrics.request(request.trigger(), Outcome.IGNORED);
            return false;
        }
        return true;
    }

    private boolean isReplyToUs(ConversationRequest request) {
        if (request.quotedContext() == null) return false;
        String hash = ContentHasher.hash(request.quotedContext());
        try {
            return sentChunks.isOurs(request.key().channelId(), hash);
        } catch (RuntimeException e) {
            log.warn("Could not check reply authorship: {}", e.toString());
            return false;
        }
    }

    private AiAnswer generateWithinBudget(ConversationRequest request) {
        Future<AiAnswer> future = executor.submit(Mdc.propagate(() -> generate(request)));
        try {
            return future.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Conversation timed out after {}", settings.timeout());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("LLM failed: {}", cause.toString());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        }
        return new AiAnswer(settings.fallbackText(), List.of(), TokenUsage.NONE, true);
    }

    private AiAnswer generate(ConversationRequest request) {
        long started = System.nanoTime();
        List<ChatTurn> turns = loadHistory(request);
        List<UserMemory> memories = searchMemories(request);
        List<LoadedAttachment> attachments = loadAttachments(request.attachments());
        LlmPrompt prompt = contextAssembler.assemble(request, turns, memories, attachments);
        metrics.latency(Stage.CONTEXT, Duration.ofNanos(System.nanoTime() - started));

        long llmStarted = System.nanoTime();
        AiAnswer answer = llm.answer(prompt);
        metrics.latency(Stage.LLM, Duration.ofNanos(System.nanoTime() - llmStarted));
        if (!answer.fallback()) usage.record(request.key().userId(), answer);
        return answer;
    }

    private List<ChatTurn> loadHistory(ConversationRequest request) {
        try {
            return history.lastTurns(request.key(), settings.maxExchanges() * 2);
        } catch (RuntimeException e) {
            log.warn("Session history unavailable, continuing without it: {}", e.toString());
            return List.of();
        }
    }

    private List<UserMemory> searchMemories(ConversationRequest request) {
        if (request.prompt().isBlank()) return List.of();
        try {
            float[] vector = embedding.embed(request.prompt());
            List<UserMemory> found = userMemory.search(request.key().guildId(), request.key().userId(),
                            vector, settings.memoryTopK(), settings.memoryMaxDistance())
                    .stream().map(ScoredMemory::memory).toList();
            if (!found.isEmpty()) touch(found);
            return found;
        } catch (RuntimeException e) {
            log.warn("Long-term memory unavailable, continuing without it: {}", e.toString());
            return List.of();
        }
    }

    private void touch(List<UserMemory> memories) {
        try {
            userMemory.touch(memories.stream().map(UserMemory::id).toList());
        } catch (RuntimeException e) {
            log.warn("Could not update memory last_used_at: {}", e.toString());
        }
    }

    private List<LoadedAttachment> loadAttachments(List<InputAttachment> attachments) {
        List<Future<LoadedAttachment>> futures = new ArrayList<>();
        List<LoadedAttachment> result = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            InputAttachment att = attachments.get(i);
            if (i >= settings.attachmentsMaxCount()) {
                result.add(LoadedAttachment.ignored(att,
                        "limite de " + settings.attachmentsMaxCount() + " anexos por mensagem"));
            } else if (att.kind() == MediaKind.UNSUPPORTED) {
                result.add(LoadedAttachment.ignored(att, "tipo de arquivo não suportado"));
            } else {
                futures.add(executor.submit(Mdc.propagate(() -> fetch(att))));
            }
        }
        List<LoadedAttachment> fetched = new ArrayList<>();
        for (Future<LoadedAttachment> f : futures) {
            try {
                fetched.add(f.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        fetched.addAll(result);
        return fetched;
    }

    private LoadedAttachment fetch(InputAttachment att) {
        byte[] data;
        try {
            data = attachmentFetcher.fetch(att);
        } catch (RuntimeException e) {
            log.warn("Attachment ignored, fetch failed: {}", e.toString());
            return LoadedAttachment.ignored(att, "não foi possível baixar o arquivo");
        }
        if (data.length > settings.attachmentsMaxBytes()) {
            return LoadedAttachment.ignored(att, "maior que " + settings.attachmentsMaxBytes() / 1_048_576 + " MB");
        }
        if (att.kind() == MediaKind.TEXT && data.length > settings.textAttachmentMaxBytes()) {
            return LoadedAttachment.ignored(att,
                    "arquivo de texto maior que " + settings.textAttachmentMaxBytes() / 1024 + " KB");
        }
        return LoadedAttachment.ok(att, data);
    }

    private void waitForPlaceholder(Instant placeholderAt) {
        if (placeholderAt == null) return;
        Duration elapsed = Duration.between(placeholderAt, clock.instant());
        Duration remaining = settings.placeholderMinDelay().minus(elapsed);
        if (remaining.isNegative() || remaining.isZero()) return;
        try {
            sleeper.sleep(remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String assistantText(List<String> chunks, List<OutboundImage> images) {
        StringBuilder text = new StringBuilder(String.join("\n", chunks));
        for (OutboundImage image : images) {
            text.append("\n[imagem enviada: ")
                    .append(image.description() == null ? image.fileName() : image.description())
                    .append(']');
        }
        return text.toString();
    }

    private void persist(ConversationRequest request, AiAnswer answer, List<String> chunks) {
        Instant now = clock.instant();
        try {
            history.append(List.of(
                    new ChatTurn(request.key(), Role.USER, ContextAssembler.historyText(request),
                            request.correlationId(), request.trigger(), null, request.receivedAt()),
                    new ChatTurn(request.key(), Role.ASSISTANT, assistantText(chunks, answer.images()),
                            request.correlationId(), request.trigger(), answer.usage(), now)));
        } catch (RuntimeException e) {
            log.warn("Could not persist session turns: {}", e.toString());
        }
        try {
            sentChunks.register(request.key(), request.correlationId(),
                    chunks.stream().map(ContentHasher::hash).toList());
        } catch (RuntimeException e) {
            log.warn("Could not register sent chunks: {}", e.toString());
        }
    }
}

