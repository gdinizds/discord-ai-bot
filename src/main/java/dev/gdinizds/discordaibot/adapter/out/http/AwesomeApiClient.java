package dev.gdinizds.discordaibot.adapter.out.http;

import dev.gdinizds.discordaibot.application.port.out.ExchangeRatePort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ExchangeRate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AwesomeApiClient implements ExchangeRatePort {

    public static final String INSTANCE = "awesomeapi";
    static final int MAX_CACHED_PAIRS = 256;
    private static final Pattern CODE = Pattern.compile("^[A-Z]{3,5}$");

    private record Cached(Optional<ExchangeRate> rate, Instant loadedAt) {}

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;
    private final Duration cacheTtl;
    private final Clock clock;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public AwesomeApiClient(RestClient.Builder builder, ObjectMapper objectMapper, Resilience resilience,
                            String baseUrl, Duration timeout, Duration cacheTtl, Clock clock) {
        this.client = RestClients.create(builder, baseUrl, timeout);
        this.objectMapper = objectMapper;
        this.resilience = resilience;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    @Override
    public Optional<ExchangeRate> latest(String from, String to) {
        String source = normalize(from);
        String target = normalize(to);
        if (!CODE.matcher(source).matches() || !CODE.matcher(target).matches() || source.equals(target)) {
            return Optional.empty();
        }
        String pair = source + "-" + target;
        Instant now = clock.instant();
        Cached cached = cache.get(pair);
        if (cached != null && cached.loadedAt().plus(cacheTtl).isAfter(now)) return cached.rate();

        Optional<ExchangeRate> rate = resilience.call(INSTANCE, () -> fetch(source, target));
        if (cache.size() >= MAX_CACHED_PAIRS) cache.clear();
        cache.put(pair, new Cached(rate, now));
        return rate;
    }

    private Optional<ExchangeRate> fetch(String from, String to) {
        String body;
        try {
            body = client.get()
                    .uri("/json/last/{pair}", from + "-" + to)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
        JsonNode quote = objectMapper.readTree(body).path(from + to);
        if (quote.isMissingNode() || quote.path("bid").isMissingNode()) return Optional.empty();
        return Optional.of(new ExchangeRate(from, to, quote.path("name").asString(),
                decimal(quote.path("bid")), decimal(quote.path("ask")),
                decimal(quote.path("high")), decimal(quote.path("low")),
                decimal(quote.path("pctChange")),
                timestamp(quote.path("timestamp"))));
    }

    static String normalize(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return new BigDecimal(node.asString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant timestamp(JsonNode node) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(node.asString().strip()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
