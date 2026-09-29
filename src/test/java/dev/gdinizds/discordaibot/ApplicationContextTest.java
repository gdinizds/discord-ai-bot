package dev.gdinizds.discordaibot;

import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.support.ScriptedChatModel;
import dev.gdinizds.discordaibot.support.TestLlmConfig;
import dev.gdinizds.discordaibot.application.port.out.PageReaderPort;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
})
@ActiveProfiles("test")
@Import(TestLlmConfig.class)
class ApplicationContextTest {

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired AiBotProperties properties;
    @Autowired CircuitBreakerRegistry circuitBreakers;
    @Autowired TimeLimiterRegistry timeLimiters;
    @Autowired RetryRegistry retries;
    @Autowired BulkheadRegistry bulkheads;
    @Autowired LlmPort llm;
    @Autowired ScriptedChatModel scriptedChatModel;

    @Test
    void bindsPropertiesWithSpecDefaults() {
        assertThat(properties.discord().botUserId()).isEqualTo("1000000000000000001");
        assertThat(properties.session().maxExchanges()).isEqualTo(20);
        assertThat(properties.memory().maxDistance()).isEqualTo(0.35);
        assertThat(properties.reply().placeholderMinDelay()).isEqualTo(Duration.ofMillis(1500));
        assertThat(properties.conversation().timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.messages().fallback()).isEqualTo("Não consegui responder agora. Tente de novo em instantes.");
        assertThat(properties.images().maxPerAnswer()).isEqualTo(4);
        assertThat(properties.images().maxBytes()).isEqualTo(8_388_608);
        assertThat(properties.images().keyPrefix()).isEqualTo("ai-bot");
    }

    @Test
    void resilienceInstancesFromYamlAreRegistered() {
        assertThat(circuitBreakers.find("gemini-chat")).isPresent();
        assertThat(circuitBreakers.find("searxng")).hasValueSatisfying(cb ->
                assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5));
        assertThat(circuitBreakers.find("database")).isPresent();
        assertThat(timeLimiters.find("gemini-chat")).hasValueSatisfying(tl ->
                assertThat(tl.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(75)));
        assertThat(timeLimiters.find("database")).isEmpty();
        assertThat(retries.find("gemini-chat")).isPresent();
        assertThat(bulkheads.find("conversation")).hasValueSatisfying(b ->
                assertThat(b.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(32));
        assertThat(circuitBreakers.find("awesomeapi")).isPresent();
        assertThat(circuitBreakers.find("web-page")).hasValueSatisfying(cb -> assertThat(cb.getCircuitBreakerConfig()
                .getIgnoreExceptionPredicate().test(new PageReaderPort.PageRejectedException("x"))).isTrue());
        assertThat(timeLimiters.find("web-page")).hasValueSatisfying(tl ->
                assertThat(tl.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(11)));
    }

    @Test
    void newToolSettingsBindWithDefaults() {
        assertThat(properties.channelLog().enabled()).isTrue();
        assertThat(properties.channelLog().retention()).isEqualTo(Duration.ofHours(48));
        assertThat(properties.reminders().maxPendingPerUser()).isEqualTo(10);
        assertThat(properties.reminders().maxAhead()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.tools().exchangeRateUrl()).isEqualTo("https://economia.awesomeapi.com.br");
        assertThat(properties.tools().readUrlMaxChars()).isEqualTo(12000);
    }

    @Test
    void withoutAFallbackModelTheLlmPortIsThePlainAdapter() {
        assertThat(llm).isInstanceOf(dev.gdinizds.discordaibot.adapter.out.gemini.GeminiLlmAdapter.class);
        assertThat(circuitBreakers.find("gemini-chat-fallback")).isPresent();
    }

    @Test
    void llmPortRunsOnTheScriptedModel() {
        scriptedChatModel.thenAnswer("oi do dublê");
        var prompt = new LlmPrompt("c", new ConversationKey("1", "2", "3"),
                "sys", List.of(), "oi", List.of());
        assertThat(llm.answer(prompt).text()).isEqualTo("oi do dublê");
    }
}

