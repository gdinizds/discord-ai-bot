package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.ConversationHistoryPort;
import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.port.out.SentChunkPort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import dev.gdinizds.discordaibot.domain.model.InputAttachment;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.MediaKind;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.model.Role;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.service.ContentHasher;
import dev.gdinizds.discordaibot.domain.service.MessageSplitter;
import dev.gdinizds.discordaibot.support.DeterministicEmbeddingModel;
import dev.gdinizds.discordaibot.support.InMemoryUserMemory;
import dev.gdinizds.discordaibot.support.RecordingReplyPublisher;
import dev.gdinizds.discordaibot.support.RecordingReplyPublisher.Kind;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    private static final String FALLBACK = "Não consegui responder agora. Tente de novo em instantes.";
    private static final String HELP = "Me mande uma pergunta.";
    private static final ConversationKey KEY = new ConversationKey("1", "2", "3");
    private static final ReplyTarget CHANNEL = new ReplyTarget.Channel("2", "99");
    private static final ReplyTarget INTERACTION = new ReplyTarget.Interaction("tok");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    private final Set<String> processed = ConcurrentHashMap.newKeySet();
    private final FakeSentChunks sentChunks = new FakeSentChunks();
    private final RecordingReplyPublisher publisher = new RecordingReplyPublisher();
    private final FakeHistory history = new FakeHistory();
    private final InMemoryUserMemory memories = new InMemoryUserMemory();
    private final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final AtomicReference<LlmPort> llm = new AtomicReference<>(
            prompt -> new AiAnswer("Camberra.", List.of("web_search"), new TokenUsage(10, 5), false));

    private Duration timeout = Duration.ofSeconds(5);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void answersOnChannelWithPlaceholderAndPersists() {
        service().handle(request(TriggerType.MENTION, "Qual a capital?", null, CHANNEL));

        assertThat(publisher.kinds()).containsExactly(Kind.PLACEHOLDER, Kind.PUBLISH);
        assertThat(publisher.last().chunks()).containsExactly("Camberra.");
        assertThat(history.turns).extracting(ChatTurn::role).containsExactly(Role.USER, Role.ASSISTANT);
        assertThat(history.turns.getLast().usage()).isEqualTo(new TokenUsage(10, 5));
        assertThat(sentChunks.isOurs("2", ContentHasher.hash("Camberra."))).isTrue();
    }

    @Test
    void imagesFromTheAnswerAreSentAndRecordedInTheSession() {
        var image = new OutboundImage("http://garage/b/ai-bot/1/c/imagem-1.png", "imagem-1.png", "Capivara no lago");
        llm.set(prompt -> new AiAnswer("Aqui está.", List.of("image_search"), TokenUsage.NONE, false, List.of(image)));

        service().handle(request(TriggerType.MENTION, "me mostra uma capivara", null, CHANNEL));

        assertThat(publisher.last().kind()).isEqualTo(Kind.PUBLISH);
        assertThat(publisher.last().images()).containsExactly(image);
        assertThat(history.turns.getLast().content()).isEqualTo("Aqui está.\n[imagem enviada: Capivara no lago]");
        assertThat(sentChunks.isOurs("2", ContentHasher.hash("Aqui está."))).isTrue();
    }

    @Test
    void fallbackNeverCarriesImages() {
        llm.set(prompt -> { throw new IllegalStateException("gemini down"); });

        service().handle(request(TriggerType.SLASH, "me mostra uma capivara", null, INTERACTION));

        assertThat(publisher.last().chunks()).containsExactly(FALLBACK);
        assertThat(publisher.last().images()).isEmpty();
    }

    @Test
    void duplicateEventIsAnsweredOnlyOnce() {
        var service = service();
        var request = request(TriggerType.SLASH, "oi", null, INTERACTION);

        service.handle(request);
        service.handle(request);

        assertThat(publisher.kinds()).containsExactly(Kind.PUBLISH);
        assertThat(llmCalls).hasValue(1);
    }

    @Test
    void replyToAMessageThatIsNotOursIsDiscarded() {
        service().handle(request(TriggerType.REPLY, "e aí?", "mensagem do downloader", CHANNEL));

        assertThat(publisher.calls).isEmpty();
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void replyToOurMessageIsAnsweredWithTheQuote() {
        sentChunks.register(KEY, "old", List.of(ContentHasher.hash("A capital é Camberra.")));
        var captured = new AtomicReference<LlmPrompt>();
        llm.set(prompt -> {
            captured.set(prompt);
            return new AiAnswer("Sydney não é a capital.", List.of(), TokenUsage.NONE, false);
        });

        service().handle(request(TriggerType.REPLY, "e Sydney?", "A capital é Camberra.\r\n", CHANNEL));

        assertThat(publisher.kinds()).containsExactly(Kind.PLACEHOLDER, Kind.PUBLISH);
        assertThat(captured.get().userText()).contains("> A capital é Camberra.", "e Sydney?");
    }

    @Test
    void waitsForThePlaceholderMinimumDelayBeforeTheFirstChunk() {
        service().handle(request(TriggerType.DOT, "rápido", null, CHANNEL));

        assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
    }

    @Test
    void interactionTargetHasNoPlaceholderNorWait() {
        service().handle(request(TriggerType.SLASH, "oi", null, INTERACTION));

        assertThat(publisher.kinds()).containsExactly(Kind.PUBLISH);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void timeoutPublishesFallbackAndPersistsNothing() {
        timeout = Duration.ofMillis(200);
        llm.set(prompt -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new AiAnswer("tarde demais", List.of(), TokenUsage.NONE, false);
        });

        service().handle(request(TriggerType.MENTION, "demora", null, CHANNEL));

        assertThat(publisher.last().chunks()).containsExactly(FALLBACK);
        assertThat(history.turns).isEmpty();
        assertThat(sentChunks.hashes).isEmpty();
    }

    @Test
    void openCircuitPublishesFallbackOnTheSameTarget() {
        llm.set(prompt -> {
            throw CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("gemini-chat"));
        });

        service().handle(request(TriggerType.SLASH, "oi", null, INTERACTION));

        assertThat(publisher.last().kind()).isEqualTo(Kind.PUBLISH);
        assertThat(publisher.last().target()).isEqualTo(INTERACTION);
        assertThat(publisher.last().chunks()).containsExactly(FALLBACK);
        assertThat(history.turns).isEmpty();
    }

    @Test
    void persistenceFailureDoesNotBlockTheAnswer() {
        history.failOnAppend = true;
        sentChunks.failOnRegister = true;

        service().handle(request(TriggerType.MENTION, "oi", null, CHANNEL));

        assertThat(publisher.kinds()).containsExactly(Kind.PLACEHOLDER, Kind.PUBLISH);
        assertThat(publisher.last().chunks()).containsExactly("Camberra.");
    }

    @Test
    void emptyMentionGetsHelpWithoutCallingTheLlm() {
        service().handle(request(TriggerType.MENTION, "", null, CHANNEL));

        assertThat(publisher.kinds()).containsExactly(Kind.DIRECT);
        assertThat(publisher.last().chunks()).containsExactly(HELP);
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void embeddingFailureContinuesWithoutMemories() {
        var service = service(new EmbeddingPort() {
            @Override public float[] embed(String text) { throw new IllegalStateException("gemini down"); }
            @Override public String modelName() { return "x"; }
        });

        service.handle(request(TriggerType.SLASH, "oi", null, INTERACTION));

        assertThat(publisher.last().chunks()).containsExactly("Camberra.");
    }

    @Test
    void unsupportedAndFailedAttachmentsAreReportedToTheModel() {
        var captured = new AtomicReference<LlmPrompt>();
        llm.set(prompt -> {
            captured.set(prompt);
            return new AiAnswer("ok", List.of(), TokenUsage.NONE, false);
        });
        var attachments = List.of(
                new InputAttachment("http://g/b/1/2/foto.png", "foto.png", MediaKind.IMAGE),
                new InputAttachment("http://g/b/1/2/app.exe", "app.exe", MediaKind.UNSUPPORTED),
                new InputAttachment("http://g/b/1/2/erro.png", "erro.png", MediaKind.IMAGE));
        var request = new ConversationRequest("c-att", TriggerType.DOT, KEY, "g", "u", "o que é isso?", null,
                attachments, CHANNEL, clock.instant());

        service().handle(request);

        assertThat(captured.get().binaries()).singleElement().satisfies(b -> assertThat(b.fileName()).isEqualTo("foto.png"));
        assertThat(captured.get().userText()).contains("[arquivo ignorado: app.exe", "[arquivo ignorado: erro.png");
        assertThat(history.turns.getFirst().content()).isEqualTo("o que é isso? [imagem: foto.png] [arquivo: app.exe] [imagem: erro.png]");
    }

    private ConversationService service() {
        return service(new EmbeddingPort() {
            @Override public float[] embed(String text) { return DeterministicEmbeddingModel.vectorOf(text); }
            @Override public String modelName() { return "test"; }
        });
    }

    private ConversationService service(EmbeddingPort embedding) {
        var settings = new ConversationSettings(20, 24_000, 5, 0.35, Duration.ofMillis(1500), timeout,
                4, 10_485_760, 102_400, ZoneId.of("America/Sao_Paulo"), FALLBACK, "ocupado", HELP);
        LlmPort countingLlm = prompt -> {
            llmCalls.incrementAndGet();
            return llm.get().answer(prompt);
        };
        return new ConversationService(
                cid -> processed.add(cid), sentChunks, publisher, history, embedding, memories,
                att -> {
                    if (att.fileName().startsWith("erro")) throw new IllegalStateException("garage down");
                    return new byte[]{1, 2, 3};
                },
                countingLlm, new ContextAssembler(settings, clock), new MessageSplitter(1900, 5, FALLBACK),
                MetricsPort.NOOP, settings, clock, executor, sleeps::add);
    }

    private ConversationRequest request(TriggerType trigger, String prompt, String quoted, ReplyTarget target) {
        return new ConversationRequest("cid-" + trigger + "-" + prompt.hashCode(), trigger, KEY, "HotBCT", "gui",
                prompt, quoted, List.of(), target, clock.instant());
    }

    private static class FakeHistory implements ConversationHistoryPort {
        final List<ChatTurn> turns = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public List<ChatTurn> lastTurns(ConversationKey key, int limit) {
            return List.copyOf(turns);
        }

        @Override
        public void append(List<ChatTurn> newTurns) {
            if (failOnAppend) throw new IllegalStateException("db down");
            turns.addAll(newTurns);
        }
    }

    private static class FakeSentChunks implements SentChunkPort {
        final Map<String, Set<String>> hashes = new ConcurrentHashMap<>();
        boolean failOnRegister;

        @Override
        public void register(ConversationKey key, String correlationId, List<String> list) {
            if (failOnRegister) throw new IllegalStateException("db down");
            hashes.computeIfAbsent(key.channelId(), k -> new HashSet<>()).addAll(list);
        }

        @Override
        public boolean isOurs(String channelId, String hash) {
            return hashes.getOrDefault(channelId, Set.of()).contains(hash);
        }
    }
}

