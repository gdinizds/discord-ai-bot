package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class FlexGeminiHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final String serviceTier;
    private final ObjectMapper objectMapper;

    public FlexGeminiHttpClient(HttpClient delegate, String serviceTier, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.serviceTier = serviceTier;
        this.objectMapper = objectMapper;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        return delegate.execute(intercept(request));
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        return delegate.executeAsync(intercept(request));
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        delegate.execute(intercept(request), listener);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(intercept(request), parser, listener);
    }

    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request) {
        return delegate.stream(intercept(request));
    }

    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        return delegate.stream(intercept(request), parser);
    }

    HttpRequest intercept(HttpRequest request) {
        if (serviceTier == null || serviceTier.isBlank() || request.body() == null) {
            return request;
        }
        if (!request.url().contains(":generateContent")) {
            return request;
        }
        try {
            JsonNode root = objectMapper.readTree(request.body());
            if (root instanceof ObjectNode objectNode) {
                if (!objectNode.has("serviceTier") || objectNode.get("serviceTier").asText().isBlank()) {
                    objectNode.put("serviceTier", serviceTier);
                    HttpRequest.Builder builder = HttpRequest.builder()
                            .url(request.url())
                            .method(request.method())
                            .headers(request.headers())
                            .body(objectMapper.writeValueAsString(objectNode));
                    if (request.formDataFields() != null) {
                        builder.formDataFields(request.formDataFields());
                    }
                    if (request.formDataFiles() != null) {
                        builder.formDataFiles(request.formDataFiles());
                    }
                    return builder.build();
                }
            }
        } catch (Exception ignored) {
        }
        return request;
    }
}
