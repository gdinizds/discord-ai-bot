package dev.gdinizds.discordaibot.adapter.out.openrouter;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class OpenRouterImageGeneratorAdapterTest {

    private Resilience resilience;

    @BeforeEach
    void setUp() {
        resilience = new Resilience(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Test
    void generatesImageSuccessfully(WireMockRuntimeInfo wireMock) {
        byte[] fakeJpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x10};
        String fakeB64 = Base64.getEncoder().encodeToString(fakeJpeg);

        wireMock.getWireMock().register(post(urlPathEqualTo("/images"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "created": 1700000000,
                                  "data": [
                                    {
                                      "b64_json": "%s",
                                      "media_type": "image/jpeg"
                                    }
                                  ]
                                }
                                """.formatted(fakeB64))));

        var properties = new AiBotProperties(null, null, null, null, null, null,
                new AiBotProperties.OpenRouter("sk-or-test-key", wireMock.getHttpBaseUrl(), "google/gemini-3.5-flash-lite", null,
                        "google/gemini-embedding-001", 768, 1.0, 1024, 5, true, true, "openai/gpt-6.1-sol", true,
                        "bytedance-seed/seedream-5-0-flash", Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(5)),
                null, null, null, null, null, null, null, null, null);

        var adapter = new OpenRouterImageGeneratorAdapter(RestClient.builder(), JsonMapper.builder().build(), resilience, properties);

        var result = adapter.generate("a cute dog in pixel art");

        assertThat(result.data()).isEqualTo(fakeJpeg);
        assertThat(result.mimeType()).isEqualTo("image/jpeg");

        wireMock.getWireMock().verify(postRequestedFor(urlPathEqualTo("/images"))
                .withHeader("Authorization", equalTo("Bearer sk-or-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("bytedance-seed/seedream-5-0-flash")))
                .withRequestBody(matchingJsonPath("$.prompt", equalTo("a cute dog in pixel art"))));
    }

    @Test
    void throwsIfApiKeyIsMissing(WireMockRuntimeInfo wireMock) {
        var properties = new AiBotProperties(null, null, null, null, null, null,
                new AiBotProperties.OpenRouter("", wireMock.getHttpBaseUrl(), "google/gemini-3.5-flash-lite", null,
                        "google/gemini-embedding-001", 768, 1.0, 1024, 5, true, true, "openai/gpt-6.1-sol", true,
                        "bytedance-seed/seedream-5-0-flash", Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(5)),
                null, null, null, null, null, null, null, null, null);

        var adapter = new OpenRouterImageGeneratorAdapter(RestClient.builder(), JsonMapper.builder().build(), resilience, properties);

        assertThatThrownBy(() -> adapter.generate("anything"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenRouter API key is missing");
    }
}
