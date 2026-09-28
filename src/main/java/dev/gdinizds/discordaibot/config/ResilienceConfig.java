package dev.gdinizds.discordaibot.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ResilienceConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService resilienceExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("resilience-", 0).factory());
    }

    @Bean
    public Resilience resilience(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries,
                                 TimeLimiterRegistry timeLimiters, BulkheadRegistry bulkheads,
                                 @Qualifier("resilienceExecutor") ExecutorService executor) {
        return new Resilience(circuitBreakers, retries, timeLimiters, bulkheads, executor);
    }

    @Bean
    public Bulkhead conversationBulkhead(BulkheadRegistry bulkheads) {
        return bulkheads.bulkhead("conversation");
    }
}

