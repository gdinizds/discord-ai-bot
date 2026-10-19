package dev.gdinizds.discordaibot.adapter.out.openrouter;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenRouterTtsAdapterTest {

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Resilience resilience;

    @BeforeEach
    void setUp() {
        wireMock.start();
        resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void synthesizesSpeechWithExpectedHeadersAndBody() {
        byte[] expectedAudio = new byte[]{(byte) 0xff, (byte) 0xfb, 0x10, 0x20};
        wireMock.stubFor(post(urlPathEqualTo("/audio/speech"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "audio/mpeg")
                        .withBody(expectedAudio)));

        var properties = new AiBotProperties(null, null, null, null, null, null,
                new AiBotProperties.OpenRouter("sk-or-test-key", wireMock.baseUrl(), "google/gemini-3.5-flash-lite", null,
                        "google/gemini-embedding-001", 768, 1.0, 1024, 5, Duration.ofSeconds(10), Duration.ofSeconds(5)),
                null, null, null, null, null, null, null, null,
                new AiBotProperties.Tts(true, "fish-audio/s2.1-pro-free:free", "24522123b5804bf691a8450d9187f03e",
                        Duration.ofSeconds(5), 1200));

        var adapter = new OpenRouterTtsAdapter(RestClient.builder(), JsonMapper.builder().build(), resilience, properties);

        byte[] result = adapter.synthesize("(happy) Bom dia!");

        assertThat(result).isEqualTo(expectedAudio);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/audio/speech"))
                .withHeader("Authorization", equalTo("Bearer sk-or-test-key"))
                .withHeader("HTTP-Referer", equalTo("https://github.com/gdinizds/discord-ai-bot"))
                .withHeader("X-Title", equalTo("Discord AI Bot"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("fish-audio/s2.1-pro-free:free")))
                .withRequestBody(matchingJsonPath("$.input", equalTo("(happy) Bom dia!")))
                .withRequestBody(matchingJsonPath("$.voice", equalTo("24522123b5804bf691a8450d9187f03e")))
                .withRequestBody(matchingJsonPath("$.response_format", equalTo("mp3"))));
    }

    @Test
    void throwsIfApiKeyIsMissing() {
        var properties = new AiBotProperties(null, null, null, null, null, null,
                new AiBotProperties.OpenRouter("", wireMock.baseUrl(), "google/gemini-3.5-flash-lite", null,
                        "google/gemini-embedding-001", 768, 1.0, 1024, 5, Duration.ofSeconds(10), Duration.ofSeconds(5)),
                null, null, null, null, null, null, null, null,
                new AiBotProperties.Tts(true, "fish-audio/s2.1-pro-free:free", "24522123b5804bf691a8450d9187f03e",
                        Duration.ofSeconds(5), 1200));

        var adapter = new OpenRouterTtsAdapter(RestClient.builder(), JsonMapper.builder().build(), resilience, properties);

        assertThatThrownBy(() -> adapter.synthesize("teste"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenRouter API key is missing");
    }
}
