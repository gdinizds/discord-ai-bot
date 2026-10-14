package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FlexGeminiHttpClientTest {

    private final HttpClient delegate = mock(HttpClient.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void injectsServiceTierFlexIntoGenerateContentRequest() throws Exception {
        var client = new FlexGeminiHttpClient(delegate, "flex", objectMapper);

        var request = HttpRequest.builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
                .method(HttpMethod.POST)
                .headers(Map.of("Content-Type", java.util.List.of("application/json")))
                .body("{\"contents\":[{\"parts\":[{\"text\":\"hi\"}]}]}")
                .build();

        client.execute(request);

        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).execute(captor.capture());

        HttpRequest sent = captor.getValue();
        JsonNode root = objectMapper.readTree(sent.body());
        assertThat(root.has("serviceTier")).isTrue();
        assertThat(root.get("serviceTier").asText()).isEqualTo("flex");
        assertThat(sent.url()).isEqualTo(request.url());
    }

    @Test
    void doesNotModifyRequestWhenServiceTierIsNull() {
        var client = new FlexGeminiHttpClient(delegate, null, objectMapper);

        var request = HttpRequest.builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
                .method(HttpMethod.POST)
                .body("{\"contents\":[]}")
                .build();

        client.execute(request);

        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).execute(captor.capture());
        assertThat(captor.getValue().body()).isEqualTo("{\"contents\":[]}");
    }

    @Test
    void doesNotModifyNonGenerateContentRequests() {
        var client = new FlexGeminiHttpClient(delegate, "flex", objectMapper);

        var request = HttpRequest.builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models")
                .method(HttpMethod.GET)
                .build();

        client.execute(request);

        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).execute(captor.capture());
        assertThat(captor.getValue()).isSameAs(request);
    }
}
