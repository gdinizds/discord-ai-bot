package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
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

