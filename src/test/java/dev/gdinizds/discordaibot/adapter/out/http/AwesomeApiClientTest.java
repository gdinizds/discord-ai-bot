package dev.gdinizds.discordaibot.adapter.out.http;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class AwesomeApiClientTest {

    private static final String USD_BRL = """
            {"USDBRL":{"code":"USD","codein":"BRL","name":"Dólar Americano/Real Brasileiro","high":"5.4312",
             "low":"5.3801","varBid":"0.02","pctChange":"0.41","bid":"5.4102","ask":"5.4132",
             "timestamp":"1790000000","create_date":"2026-09-21 13:53:20"}}
            """;

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T12:00:00Z"));
    private AwesomeApiClient client;

    @BeforeEach
    void setUp() {
        wireMock.start();
        var resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);
        client = new AwesomeApiClient(RestClient.builder(), JsonMapper.builder().build(), resilience,
                wireMock.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(60), clock);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void parsesTheQuoteAndNormalizesCodes() {
        wireMock.stubFor(get(urlPathEqualTo("/json/last/USD-BRL")).willReturn(json(USD_BRL)));

        var rate = client.latest(" usd ", "brl").orElseThrow();

        assertThat(rate.from()).isEqualTo("USD");
        assertThat(rate.to()).isEqualTo("BRL");
        assertThat(rate.name()).isEqualTo("Dólar Americano/Real Brasileiro");
        assertThat(rate.bid()).isEqualByComparingTo("5.4102");
        assertThat(rate.ask()).isEqualByComparingTo("5.4132");
        assertThat(rate.pctChange()).isEqualByComparingTo("0.41");
        assertThat(rate.updatedAt()).isEqualTo(Instant.ofEpochSecond(1790000000));
    }

    @Test
    void quotesAreCachedUntilTheTtlExpires() {
        wireMock.stubFor(get(urlPathEqualTo("/json/last/USD-BRL")).willReturn(json(USD_BRL)));

        client.latest("USD", "BRL");
        client.latest("USD", "BRL");
        clock.advance(Duration.ofSeconds(61));
        client.latest("USD", "BRL");

        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/json/last/USD-BRL")));
    }

    @Test
    void unknownPairIsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/json/last/XYZ-BRL")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"code\":\"CoinNotExists\"}")));

        assertThat(client.latest("XYZ", "BRL")).isEmpty();
    }

    @Test
    void invalidCodesNeverReachTheApi() {
        assertThat(client.latest("US", "BRL")).isEmpty();
        assertThat(client.latest("USD", "USD")).isEmpty();
        assertThat(client.latest("USD/../x", "BRL")).isEmpty();
        assertThat(client.latest(null, "BRL")).isEmpty();

        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
