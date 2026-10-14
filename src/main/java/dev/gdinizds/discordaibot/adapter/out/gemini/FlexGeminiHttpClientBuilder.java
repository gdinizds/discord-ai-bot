package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public class FlexGeminiHttpClientBuilder implements HttpClientBuilder {

    private final HttpClientBuilder delegate = HttpClientBuilderLoader.loadHttpClientBuilder();
    private final String serviceTier;
    private final ObjectMapper objectMapper;

    public FlexGeminiHttpClientBuilder(String serviceTier, ObjectMapper objectMapper) {
        this.serviceTier = serviceTier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        return new FlexGeminiHttpClient(delegate.build(), serviceTier, objectMapper);
    }
}
