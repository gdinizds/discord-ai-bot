package dev.gdinizds.discordaibot.adapter.out.openrouter;

import dev.gdinizds.discordaibot.application.port.out.TtsPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

public class OpenRouterTtsAdapter implements TtsPort {

    static final String INSTANCE = "openrouter-tts";
    private static final Logger log = LoggerFactory.getLogger(OpenRouterTtsAdapter.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;
    private final AiBotProperties properties;

    public OpenRouterTtsAdapter(RestClient.Builder builder, ObjectMapper objectMapper,
                                Resilience resilience, AiBotProperties properties) {
        this.objectMapper = objectMapper;
        this.resilience = resilience;
        this.properties = properties;

        var openrouter = properties.openrouter();
        var tts = properties.tts();
        var timeout = tts != null && tts.timeout() != null ? tts.timeout() : Duration.ofSeconds(25);

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
    public byte[] synthesize(String text) {
        String apiKey = properties.openrouter() != null ? properties.openrouter().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is missing for TTS");
        }
        var tts = properties.tts();
        String model = tts != null && tts.model() != null && !tts.model().isBlank()
                ? tts.model()
                : "fish-audio/s2.1-pro-free:free";
        String voice = tts != null && tts.voice() != null && !tts.voice().isBlank()
                ? tts.voice()
                : "24522123b5804bf691a8450d9187f03e";

        Map<String, Object> payload = Map.of(
                "model", model,
                "input", text,
                "voice", voice,
                "response_format", "mp3"
        );

        return resilience.call(INSTANCE, () -> {
            String jsonBody = objectMapper.writeValueAsString(payload);
            return client.post()
                    .uri("/audio/speech")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(byte[].class);
        });
    }
}
