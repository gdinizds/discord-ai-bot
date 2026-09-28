package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailoverLlmAdapterTest {

    private static final LlmPrompt PROMPT = new LlmPrompt("c1", new ConversationKey("1", "2", "3"),
            "sys", List.of(), "oi", List.of());

    private final AtomicInteger fallbackCalls = new AtomicInteger();
    private final LlmPort fallback = prompt -> {
        fallbackCalls.incrementAndGet();
        return new AiAnswer("resposta do reserva", List.of(), TokenUsage.NONE, false);
    };

    @Test
    void primaryAnswerIsUsedWhenItWorks() {
        var adapter = new FailoverLlmAdapter(prompt -> new AiAnswer("principal", List.of(), TokenUsage.NONE, false),
                fallback, "gemini-3.5-flash");

        assertThat(adapter.answer(PROMPT).text()).isEqualTo("principal");
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void overloadedPrimaryFallsBackToTheSecondModel() {
        var adapter = new FailoverLlmAdapter(prompt -> {
            throw new ServiceUnavailableException(new RuntimeException("503 high demand"));
        }, fallback, "gemini-3.5-flash");

        assertThat(adapter.answer(PROMPT).text()).isEqualTo("resposta do reserva");
    }

    @Test
    void rateLimitAndOpenCircuitAlsoFallBack() {
        var rateLimited = new FailoverLlmAdapter(prompt -> {
            throw new RateLimitedException(new RuntimeException("429"));
        }, fallback, "m");
        var circuitOpen = new FailoverLlmAdapter(prompt -> {
            throw CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("gemini-chat"));
        }, fallback, "m");
        var wrapped = new FailoverLlmAdapter(prompt -> {
            throw new Resilience.ExternalCallException("gemini-chat",
                    new ServiceUnavailableException(new RuntimeException("503")));
        }, fallback, "m");

        rateLimited.answer(PROMPT);
        circuitOpen.answer(PROMPT);
        wrapped.answer(PROMPT);

        assertThat(fallbackCalls).hasValue(3);
    }

    @Test
    void invalidRequestIsNotRetriedOnTheFallback() {
        var adapter = new FailoverLlmAdapter(prompt -> {
            throw new InvalidLlmRequestException(new RuntimeException("400"));
        }, fallback, "m");

        assertThatThrownBy(() -> adapter.answer(PROMPT)).isInstanceOf(InvalidLlmRequestException.class);
        assertThat(fallbackCalls).hasValue(0);
    }
}
