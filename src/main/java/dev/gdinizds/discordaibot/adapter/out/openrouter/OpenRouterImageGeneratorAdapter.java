package dev.gdinizds.discordaibot.adapter.out.openrouter;

import dev.gdinizds.discordaibot.application.port.out.ImageGeneratePort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public class OpenRouterImageGeneratorAdapter implements ImageGeneratePort {

    static final String INSTANCE = "openrouter-image";
    private static final Logger log = LoggerFactory.getLogger(OpenRouterImageGeneratorAdapter.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;
    private final AiBotProperties properties;

    public OpenRouterImageGeneratorAdapter(RestClient.Builder builder, ObjectMapper objectMapper,
                                           Resilience resilience, AiBotProperties properties) {
        this.objectMapper = objectMapper;
        this.resilience = resilience;
        this.properties = properties;

        var openrouter = properties.openrouter();
        var timeout = openrouter != null && openrouter.imageGenerateTimeout() != null
                ? openrouter.imageGenerateTimeout()
                : Duration.ofSeconds(60);

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);

        String baseUrl = openrouter != null && openrouter.baseUrl() != null && !openrouter.baseUrl().isBlank()
                ? openrouter.baseUrl()
                : "https://openrouter.ai/api/v1";

        this.client = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("HTTP-Referer", "https://github.com/gdinizds/discord-ai-bot")
                .defaultHeader("X-Title", "Discord AI Bot")
                .build();
    }

    @Override
    public GeneratedImage generate(String prompt) {
        String apiKey = properties.openrouter() != null ? properties.openrouter().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is missing for image generation");
        }
        var openrouter = properties.openrouter();
        String model = openrouter != null && openrouter.imageModel() != null && !openrouter.imageModel().isBlank()
                ? openrouter.imageModel()
                : "bytedance-seed/seedream-5-0-flash";

        Map<String, Object> payload = Map.of(
                "model", model,
                "prompt", prompt,
                "n", 1
        );

        return resilience.call(INSTANCE, () -> {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String response = client.post()
                    .uri("/images")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Empty response from OpenRouter image generation");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("No image data returned from OpenRouter");
            }

            JsonNode first = data.get(0);
            String b64 = first.path("b64_json").asText(null);
            if (b64 != null && !b64.isBlank()) {
                byte[] bytes = Base64.getDecoder().decode(b64);
                String mediaType = first.path("media_type").asText("image/jpeg");
                return new GeneratedImage(bytes, mediaType);
            }

            throw new IllegalStateException("Missing b64_json in image generation response");
        });
    }
}
