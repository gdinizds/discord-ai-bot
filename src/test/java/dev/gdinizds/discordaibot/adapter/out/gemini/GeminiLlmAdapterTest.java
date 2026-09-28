package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ToolSupport;
import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.BinaryPart;
import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.MediaKind;
import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.Role;
import dev.gdinizds.discordaibot.domain.model.SearchHit;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import dev.gdinizds.discordaibot.support.ScriptedChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiLlmAdapterTest {

    private static final ConversationKey KEY = new ConversationKey("1", "2", "3");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScriptedChatModel model = new ScriptedChatModel();
    private final List<String> searches = new ArrayList<>();
    private final RetryRegistry retries = RetryRegistry.ofDefaults();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void toolRunsAndItsResultGoesBackToTheModel() {
        model.thenCallTool("web_search", "{\"query\":\"java 25 lançamento\",\"limit\":1}")
                .thenAnswer("O Java 25 saiu em setembro de 2025.");

        AiAnswer answer = adapter().answer(prompt("Quando saiu o Java 25?", List.of()));

        assertThat(searches).containsExactly("java 25 lançamento");
        assertThat(answer.text()).isEqualTo("O Java 25 saiu em setembro de 2025.");
        assertThat(answer.toolsUsed()).containsExactly("web_search");
        assertThat(answer.usage().input()).isEqualTo(200);
        assertThat(answer.usage().output()).isEqualTo(40);

        assertThat(model.requests().getFirst().messages().getLast()).isInstanceOfSatisfying(UserMessage.class,
                user -> assertThat(user.singleText()).isEqualTo("Quando saiu o Java 25?"));
        List<ChatMessage> secondCall = model.requests().get(1).messages();
        assertThat(secondCall.getLast()).isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                result -> assertThat(result.text()).contains("Java 25 GA", "https://openjdk.org/projects/jdk/25/"));
    }

    @Test
    void historySystemPromptAndImageReachTheModel() {
        model.thenAnswer("É um gato.");
        var history = List.of(
                new ChatTurn(KEY, Role.USER, "oi", "c0", TriggerType.DOT, null, Instant.EPOCH),
                new ChatTurn(KEY, Role.ASSISTANT, "olá!", "c0", TriggerType.DOT, null, Instant.EPOCH));
        var image = new BinaryPart("gato.png", MediaKind.IMAGE, "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        adapter().answer(new LlmPrompt("c1", KEY, "SYSTEM", history, "o que é isso?", List.of(image)));

        List<ChatMessage> messages = model.requests().getFirst().messages();
        assertThat(messages.getFirst()).isInstanceOfSatisfying(SystemMessage.class, s -> assertThat(s.text()).isEqualTo("SYSTEM"));
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(messages.getLast()).isInstanceOfSatisfying(UserMessage.class, current -> {
            assertThat(current.contents()).hasSize(2);
            assertThat(current.contents().getFirst()).isInstanceOfSatisfying(TextContent.class,
                    t -> assertThat(t.text()).isEqualTo("o que é isso?"));
            assertThat(current.contents().getLast()).isInstanceOf(ImageContent.class);
        });
    }

    @Test
    void imageSearchAttachesImagesToTheAnswer() {
        model.thenCallTool("image_search", "{\"query\":\"capivara\",\"count\":1}")
                .thenAnswer("Aqui está uma capivara.");

        AiAnswer answer = adapter().answer(prompt("me mostra uma capivara", List.of()));

        assertThat(answer.toolsUsed()).containsExactly("image_search");
        assertThat(answer.images()).singleElement().satisfies(image -> {
            assertThat(image.url()).isEqualTo("http://garage:3900/b/ai-bot/1/c1/imagem-1.jpg");
            assertThat(image.description()).isEqualTo("Capivara no lago");
        });
        assertThat(model.requests().get(1).messages().getLast()).isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                result -> assertThat(result.text()).contains("Anexei 1 imagem", "Capivara no lago", "Wikimedia"));
    }

    @Test
    void answerWithoutImageToolHasNoImages() {
        model.thenAnswer("oi");

        assertThat(adapter().answer(prompt("oi", List.of())).images()).isEmpty();
    }

    @Test
    void rateLimitIsTranslatedAndRetried() {
        retries.retry("gemini-chat", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(RateLimitedException.class)
                .build());
        model.thenThrow(new RateLimitException("429")).thenAnswer("depois do retry");

        assertThat(adapter().answer(prompt("oi", List.of())).text()).isEqualTo("depois do retry");
    }

    private GeminiLlmAdapter adapter() {
        var resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), retries,
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);
        var support = new ToolSupport(MetricsPort.NOOP, executor, 4000);
        var properties = new AiBotProperties(null, null, null, null, null, null,
                new AiBotProperties.Gemini("", "gemini-test", "gemini-embedding-001", 768, 0.7, 2048, 5,
                        Duration.ofSeconds(75), Duration.ofSeconds(5)),
                new AiBotProperties.Tools("http://searxng", "http://geo", "http://forecast", "http://{lang}.wiki",
                        "ua", 4000, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                null, null, null,
                new AiBotProperties.Images(4, Duration.ofSeconds(5), Duration.ofSeconds(1), 1_000_000, "ai-bot", "ua"));
        ManageMemoryUseCase memories = new ManageMemoryUseCase() {
            @Override public MemorySaveResult save(String g, String u, String c, String cat, String cid) { return MemorySaveResult.INSERTED; }
            @Override public List<UserMemory> list(String g, String u) { return List.of(); }
            @Override public boolean forget(String g, String u, long id) { return false; }
            @Override public int forgetAll(String g, String u) { return 0; }
        };
        var factory = new AssistantFactory(new ResilientChatModel(model, resilience),
                (query, limit) -> {
                    searches.add(query);
                    return List.of(new SearchHit("Java 25 GA", "https://openjdk.org/projects/jdk/25/", "Released 2025-09-16"));
                },
                (city, days) -> Optional.empty(),
                (query, lang) -> Optional.empty(),
                memories,
                (query, limit) -> List.of(new ImageHit("Capivara no lago", "https://img.example/capivara.jpg",
                        "https://example.org/capivara", "Wikimedia", "800 x 600")),
                (guildId, correlationId, index, hit) -> new OutboundImage(
                        "http://garage:3900/b/ai-bot/" + guildId + "/" + correlationId + "/imagem-" + index + ".jpg",
                        "imagem-" + index + ".jpg", hit.title()),
                support, properties);
        return new GeminiLlmAdapter(factory);
    }

    private static LlmPrompt prompt(String text, List<BinaryPart> binaries) {
        return new LlmPrompt("c1", KEY, "Você é o HotBCT.", List.of(), text, binaries);
    }
}

