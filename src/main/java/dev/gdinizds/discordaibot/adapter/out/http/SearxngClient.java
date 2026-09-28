package dev.gdinizds.discordaibot.adapter.out.http;

import dev.gdinizds.discordaibot.application.port.out.ImageSearchPort;
import dev.gdinizds.discordaibot.application.port.out.WebSearchPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.SearchHit;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearxngClient implements WebSearchPort, ImageSearchPort {

    static final String INSTANCE = "searxng";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public SearxngClient(RestClient.Builder builder, ObjectMapper objectMapper, Resilience resilience,
                         String baseUrl, Duration timeout) {
        this.client = RestClients.create(builder, baseUrl, timeout);
        this.objectMapper = objectMapper;
        this.resilience = resilience;
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        String body = resilience.call(INSTANCE, () -> client.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("language", "pt-BR")
                        .queryParam("safesearch", 1)
                        .build())
                .retrieve()
                .body(String.class));

        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode result : objectMapper.readTree(body).path("results")) {
            if (hits.size() >= limit) break;
            String url = result.path("url").asString("");
            if (url.isBlank()) continue;
            hits.add(new SearchHit(result.path("title").asString(""), url, result.path("content").asString("")));
        }
        return hits;
    }

    @Override
    public List<ImageHit> searchImages(String query, int limit) {
        String body = resilience.call(INSTANCE, () -> client.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("categories", "images")
                        .queryParam("safesearch", 2)
                        .build())
                .retrieve()
                .body(String.class));

        List<ImageHit> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode result : objectMapper.readTree(body).path("results")) {
            if (hits.size() >= limit) break;
            String imageUrl = result.path("img_src").asString("").strip();
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) continue;
            if (!seen.add(imageUrl)) continue;
            String source = result.path("source").asString("");
            if (source.isBlank()) source = result.path("engine").asString("");
            hits.add(new ImageHit(
                    result.path("title").asString(""),
                    imageUrl,
                    result.path("url").asString(""),
                    source,
                    result.path("resolution").asString("")));
        }
        return hits;
    }
}
