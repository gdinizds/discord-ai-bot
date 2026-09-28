package dev.gdinizds.discordaibot.adapter.out.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordaibot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearxngCircuitBreakerTest {

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private SearxngClient client;

    @BeforeEach
    void setUp() {
        wireMock.start();
        var circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        circuitBreakers.circuitBreaker("searxng", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        var timeLimiters = TimeLimiterRegistry.ofDefaults();
        timeLimiters.timeLimiter("searxng", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build());
        var resilience = new Resilience(circuitBreakers, RetryRegistry.ofDefaults(), timeLimiters,
                BulkheadRegistry.ofDefaults(), executor);
        client = new SearxngClient(RestClient.builder(), JsonMapper.builder().build(), resilience,
                wireMock.baseUrl(), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void parsesResultsAndRespectsLimit() {
        wireMock.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"results":[
                          {"title":"A","url":"https://a","content":"aa"},
                          {"title":"B","url":"https://b","content":"bb"},
                          {"title":"C","url":"https://c","content":"cc"}]}
                        """)));

        assertThat(client.search("java", 2)).extracting("url").containsExactly("https://a", "https://b");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("format", com.github.tomakehurst.wiremock.client.WireMock.equalTo("json"))
                .withQueryParam("language", com.github.tomakehurst.wiremock.client.WireMock.equalTo("pt-BR"))
                .withQueryParam("safesearch", com.github.tomakehurst.wiremock.client.WireMock.equalTo("1")));
    }

    @Test
    void failuresOpenTheCircuitAndLaterCallsDoNotReachTheServer() {
        wireMock.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.search("java", 3)).isNotInstanceOf(CallNotPermittedException.class);
        }
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.search("java", 3)).isInstanceOf(CallNotPermittedException.class);
        }

        wireMock.verify(5, getRequestedFor(urlPathEqualTo("/search")));
    }
}

