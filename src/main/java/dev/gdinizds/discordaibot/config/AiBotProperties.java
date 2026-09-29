package dev.gdinizds.discordaibot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties("ai-bot")
public record AiBotProperties(
        @Valid @NotNull Discord discord,
        @Valid @NotNull Topics topics,
        @Valid @NotNull @DefaultValue Session session,
        @Valid @NotNull @DefaultValue Memory memory,
        @Valid @NotNull @DefaultValue Reply reply,
        @Valid @NotNull @DefaultValue Attachments attachments,
        @Valid @NotNull Gemini gemini,
        @Valid @NotNull Tools tools,
        @Valid @NotNull S3 s3,
        @Valid @NotNull @DefaultValue Conversation conversation,
        @Valid @NotNull @DefaultValue Messages messages,
        @Valid @NotNull @DefaultValue Images images,
        @Valid @NotNull @DefaultValue Limits limits,
        @Valid @NotNull @DefaultValue ChannelLog channelLog,
        @Valid @NotNull @DefaultValue Reminders reminders) {

    public record Discord(@NotBlank String botUserId) {}

    public record Topics(
            @NotBlank String inboundInteractions,
            @NotBlank String inboundCommands,
            @NotBlank String inboundMessages,
            @NotBlank String outboundResponses,
            @NotBlank String gatewayCommands) {}

    public record Session(
            @DefaultValue("20") @Positive int maxExchanges,
            @DefaultValue("24000") @Positive int maxHistoryChars) {}

    public record Memory(
            @DefaultValue("5") @Positive int topK,
            @DefaultValue("0.35") double maxDistance,
            @DefaultValue("0.08") double dedupDistance,
            @DefaultValue("200") @Positive int maxPerUser,
            @DefaultValue("500") @Positive int maxContentChars) {}

    public record Reply(
            @DefaultValue("1900") @Positive int chunkLimit,
            @DefaultValue("5") @Positive int maxChunks,
            @DefaultValue("Pensando...") String placeholderText,
            @DefaultValue("1500ms") Duration placeholderMinDelay) {}

    public record Attachments(
            @DefaultValue("4") @Positive int maxCount,
            @DefaultValue("10485760") @Positive long maxBytes,
            @DefaultValue("102400") @Positive long textMaxBytes) {}

    public record Gemini(
            String apiKey,
            @NotBlank String chatModel,
            @DefaultValue("gemini-embedding-001") @NotBlank String embeddingModel,
            @DefaultValue("768") @Positive int embeddingDimensions,
            @DefaultValue("0.7") double temperature,
            @DefaultValue("2048") @Positive int maxOutputTokens,
            @DefaultValue("5") @Positive int maxSequentialToolInvocations,
            @DefaultValue("75s") Duration chatTimeout,
            @DefaultValue("5s") Duration embeddingTimeout) {

        public String requireApiKey() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "ai-bot.gemini.api-key is missing; it must come from /vault/secrets/gemini.yaml");
            }
            return apiKey;
        }
    }

    public record Tools(
            @NotBlank String searxngUrl,
            @DefaultValue("https://geocoding-api.open-meteo.com") String openMeteoGeocodingUrl,
            @DefaultValue("https://api.open-meteo.com") String openMeteoForecastUrl,
            @DefaultValue("https://{lang}.wikipedia.org") String wikipediaUrl,
            @NotBlank String wikipediaUserAgent,
            @DefaultValue("4000") @Positive int maxResultChars,
            @DefaultValue("5s") Duration searxngTimeout,
            @DefaultValue("4s") Duration openMeteoTimeout,
            @DefaultValue("4s") Duration wikipediaTimeout,
            @DefaultValue("5s") Duration saveMemoryTimeout,
            @DefaultValue("12s") Duration readUrlTimeout,
            @DefaultValue("12000") @Positive int readUrlMaxChars,
            @DefaultValue("2097152") @Positive long readUrlMaxBytes,
            @DefaultValue("https://economia.awesomeapi.com.br") String exchangeRateUrl,
            @DefaultValue("5s") Duration exchangeRateTimeout,
            @DefaultValue("60s") Duration exchangeRateCacheTtl,
            @DefaultValue("3s") Duration channelContextTimeout,
            @DefaultValue("8000") @Positive int channelContextMaxChars,
            @DefaultValue("5s") Duration reminderTimeout,
            @DefaultValue("1s") Duration calculatorTimeout) {}

    public record ChannelLog(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("48h") Duration retention,
            @DefaultValue("1000") @Positive int maxContentChars) {}

    public record Reminders(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1m") Duration minDelay,
            @DefaultValue("30d") Duration maxAhead,
            @DefaultValue("10") @Positive int maxPendingPerUser,
            @DefaultValue("500") @Positive int maxTextChars,
            @DefaultValue("50") @Positive int dispatchBatch) {}

    public record S3(
            @NotBlank String endpoint,
            @NotBlank String bucket,
            String accessKey,
            String secretKey,
            @DefaultValue("garage") String region,
            @DefaultValue("10s") Duration timeout) {}

    public record Limits(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("6") @Positive int perUserPerMinute,
            @DefaultValue("150") @Positive int perUserDailyRequests,
            @DefaultValue("5.00") double perUserDailyUsd,
            @DefaultValue("10.00") double perUserMonthlyUsd,
            @DefaultValue("20.00") double monthlyUsd,
            @DefaultValue("1.50") double defaultInputPrice,
            @DefaultValue("9.00") double defaultOutputPrice,
            Map<String, Price> prices,
            List<String> exemptUserIds,
            @DefaultValue("Muitas perguntas seguidas. Espere um minuto e tente de novo.") String burstLimitMessage,
            @DefaultValue("Você atingiu seu limite diário de uso da IA. Tente de novo amanhã.") String userLimitMessage,
            @DefaultValue("Você atingiu seu limite mensal de uso da IA. Ele renova no começo do próximo mês.")
            String userMonthlyLimitMessage,
            @DefaultValue("O limite mensal de uso da IA do servidor foi atingido. Ela volta no começo do próximo mês.")
            String monthlyLimitMessage) {

        public record Price(double input, double output) {}
    }

    public record Images(
            @DefaultValue("4") @Positive int maxPerAnswer,
            @DefaultValue("25s") Duration searchTimeout,
            @DefaultValue("5s") Duration downloadTimeout,
            @DefaultValue("8388608") @Positive long maxBytes,
            @DefaultValue("ai-bot") @NotBlank String keyPrefix,
            @DefaultValue("Mozilla/5.0 (compatible; discord-ai-bot/1.0; +https://github.com/gdinizds/discord-ai-bot)")
            @NotBlank String userAgent) {}

    public record Conversation(
            @DefaultValue("90s") Duration timeout,
            @DefaultValue("America/Sao_Paulo") String zone) {}

    public record Messages(
            @DefaultValue("Não consegui responder agora. Tente de novo em instantes.") String fallback,
            @DefaultValue("Estou ocupado com muitas perguntas agora, tente em instantes.") String busy,
            @DefaultValue("Me mande uma pergunta: `.ia sua pergunta`, `/ia` ou uma menção com o texto. "
                    + "Imagens, PDFs e arquivos de texto funcionam no `.ia` e na menção.") String help) {}
}

