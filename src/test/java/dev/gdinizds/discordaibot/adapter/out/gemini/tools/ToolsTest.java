package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.SearchHit;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> toolCalls = new ArrayList<>();
    private final ToolSupport support = new ToolSupport(new MetricsPort() {
        @Override public void request(TriggerType trigger, Outcome outcome) {}
        @Override public void latency(Stage stage, Duration duration) {}
        @Override public void tokens(TokenUsage usage) {}
        @Override public void toolCall(String tool, boolean success) { toolCalls.add(tool + ":" + success); }
        @Override public void memoryWrite(MemoryWrite action) {}
        @Override public void replyChunks(int count) {}
    }, executor, 4000);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void exceptionBecomesUnavailableText() {
        var tool = new WebSearchTool((q, n) -> { throw new IllegalStateException("searxng 500"); }, support, Duration.ofSeconds(1));

        assertThat(tool.webSearch("java 25", 3)).isEqualTo("Busca indisponível no momento.");
        assertThat(toolCalls).containsExactly("web_search:false");
    }

    @Test
    void timeoutBecomesUnavailableText() {
        var tool = new WeatherTool((city, days) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }, support, Duration.ofMillis(100));

        assertThat(tool.weather("Recife", 1)).isEqualTo("Previsão do tempo indisponível no momento.");
    }

    @Test
    void resultIsCutAt4000Characters() {
        var hits = new ArrayList<SearchHit>();
        for (int i = 0; i < 5; i++) hits.add(new SearchHit("t".repeat(200), "https://x/" + "u".repeat(2000), "s".repeat(300)));
        var tool = new WebSearchTool((q, n) -> hits, support, Duration.ofSeconds(1));

        String result = tool.webSearch("x", 5);

        assertThat(result).hasSizeLessThanOrEqualTo(4000).endsWith("[resultado cortado]");
        assertThat(toolCalls).containsExactly("web_search:true");
    }

    @Test
    void snippetIsLimitedTo300Characters() {
        var tool = new WebSearchTool((q, n) -> List.of(new SearchHit("Título", "https://a", "s".repeat(1000))),
                support, Duration.ofSeconds(1));

        String snippetLine = tool.webSearch("x", 1).lines().filter(l -> l.contains("sss")).findFirst().orElseThrow();

        assertThat(snippetLine.strip()).hasSize(300);
    }

    @Test
    void wikipediaDefaultsToPortuguese() {
        var langs = new ArrayList<String>();
        var tool = new WikipediaTool((q, lang) -> {
            langs.add(lang);
            return Optional.empty();
        }, support, Duration.ofSeconds(1));

        tool.wikipedia("Recife", null);
        tool.wikipedia("Recife", "../etc");

        assertThat(langs).containsExactly("pt", "pt");
    }

    @Test
    void imageSearchSkipsCandidatesThatFailAndStopsAtTheRequestedCount() {
        var hits = List.of(hit("quebrada"), hit("boa 1"), hit("boa 2"), hit("boa 3"));
        var limits = new ArrayList<Integer>();
        var tool = new ImageSearchTool((q, n) -> {
            limits.add(n);
            return hits;
        }, (g, c, i, h) -> {
            if (h.title().equals("quebrada")) throw new IllegalStateException("404");
            return new OutboundImage("http://garage/b/" + g + "/" + c + "/imagem-" + i + ".png", "imagem-" + i + ".png", h.title());
        }, support, Duration.ofSeconds(2), "g1", "c1", 4);

        String result = tool.imageSearch("capivara", 2);

        assertThat(limits).containsExactly(6);
        assertThat(result).startsWith("Anexei 2 imagens").contains("boa 1", "boa 2").doesNotContain("boa 3");
        assertThat(tool.attached()).extracting(OutboundImage::url)
                .containsExactly("http://garage/b/g1/c1/imagem-1.png", "http://garage/b/g1/c1/imagem-2.png");
        assertThat(toolCalls).containsExactly("image_search:true");
    }

    @Test
    void imageSearchRespectsTheLimitPerAnswerAcrossCalls() {
        var tool = new ImageSearchTool((q, n) -> List.of(hit("a"), hit("b"), hit("c")),
                (g, c, i, h) -> new OutboundImage("u" + i, "imagem-" + i + ".png", h.title()),
                support, Duration.ofSeconds(2), "g1", "c1", 2);

        tool.imageSearch("x", 4);
        String second = tool.imageSearch("y", 1);

        assertThat(tool.attached()).hasSize(2);
        assertThat(second).isEqualTo("Limite de 2 imagens por resposta atingido.");
    }

    @Test
    void imageSearchReportsWhenNothingCouldBeSent() {
        var empty = new ImageSearchTool((q, n) -> List.of(),
                (g, c, i, h) -> { throw new AssertionError(); }, support, Duration.ofSeconds(2), "g1", "c1", 4);
        var failing = new ImageSearchTool((q, n) -> List.of(hit("a")),
                (g, c, i, h) -> { throw new IllegalStateException("403"); }, support, Duration.ofSeconds(2), "g1", "c1", 4);

        assertThat(empty.imageSearch("x", 1)).isEqualTo("Nenhuma imagem encontrada para essa busca.");
        assertThat(failing.imageSearch("x", 1)).startsWith("Encontrei imagens, mas nenhuma pôde ser baixada");
        assertThat(failing.attached()).isEmpty();
    }

    @Test
    void imageSearchFailureBecomesUnavailableText() {
        var tool = new ImageSearchTool((q, n) -> { throw new IllegalStateException("searxng down"); },
                (g, c, i, h) -> null, support, Duration.ofSeconds(2), "g1", "c1", 4);

        assertThat(tool.imageSearch("x", 1)).isEqualTo("Busca de imagens indisponível no momento.");
        assertThat(toolCalls).containsExactly("image_search:false");
    }

    @Test
    void imageFailureReasonUsesTheRootCause() {
        var wrapped = new IllegalStateException("garage",
                new RuntimeException("Access Denied: key discord-ai-bot has no write permission on bucket discord-gateway-attachments"));

        assertThat(ImageSearchTool.reason(wrapped))
                .startsWith("RuntimeException: Access Denied: key discord-ai-bot")
                .hasSizeLessThanOrEqualTo("RuntimeException: ".length() + 80);
        assertThat(ImageSearchTool.reason(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    private static ImageHit hit(String title) {
        return new ImageHit(title, "https://img.example/" + title.replace(' ', '-') + ".png",
                "https://example.org/" + title.replace(' ', '-'), "Exemplo", "640 x 480");
    }

    @Test
    void saveMemoryIsBoundToTheRequestOwner() {
        var owners = new ArrayList<String>();
        ManageMemoryUseCase memories = new ManageMemoryUseCase() {
            @Override
            public MemorySaveResult save(String guildId, String userId, String content, String category, String cid) {
                owners.add(guildId + "/" + userId + "/" + cid);
                return MemorySaveResult.REJECTED_SENSITIVE;
            }
            @Override public List<UserMemory> list(String g, String u) { return List.of(); }
            @Override public boolean forget(String g, String u, long id) { return false; }
            @Override public int forgetAll(String g, String u) { return 0; }
        };
        var tool = new SaveMemoryTool(memories, support, Duration.ofSeconds(1), "g1", "u1", "c1");

        assertThat(tool.saveMemory("CPF 123.456.789-09", "FACT")).contains("dados sensíveis");
        assertThat(owners).containsExactly("g1/u1/c1");
    }
}

