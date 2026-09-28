package dev.gdinizds.discordaibot.adapter.out.http;

import dev.gdinizds.discordaibot.application.port.out.EncyclopediaPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ArticleSummary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

public class WikipediaClient implements EncyclopediaPort {

    static final String INSTANCE = "wikipedia";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;
    private final String baseUrlTemplate;

    public WikipediaClient(RestClient.Builder builder, ObjectMapper objectMapper, Resilience resilience,
                           String baseUrlTemplate, String userAgent, Duration timeout) {
        this.client = RestClients.create(builder.clone().defaultHeader(HttpHeaders.USER_AGENT, userAgent), "", timeout);
        this.objectMapper = objectMapper;
        this.resilience = resilience;
        this.baseUrlTemplate = baseUrlTemplate;
    }

    @Override
    public Optional<ArticleSummary> summary(String query, String lang) {
        String base = baseUrlTemplate.replace("{lang}", lang);
        return resilience.call(INSTANCE, () -> findPage(base, query).flatMap(key -> fetchSummary(base, key)));
    }

    private Optional<String> findPage(String base, String query) {
        String body = client.get()
                .uri(base + "/w/rest.php/v1/search/page?q={q}&limit=1", query)
                .retrieve()
                .body(String.class);
        String key = objectMapper.readTree(body).path("pages").path(0).path("key").asString("");
        return key.isBlank() ? Optional.empty() : Optional.of(key);
    }

    private Optional<ArticleSummary> fetchSummary(String base, String key) {
        String body;
        try {
            body = client.get()
                    .uri(base + "/api/rest_v1/page/summary/{title}", key)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(body);
        return Optional.of(new ArticleSummary(
                root.path("title").asString(key),
                root.path("extract").asString(""),
                root.path("content_urls").path("desktop").path("page").asString("")));
    }
}

