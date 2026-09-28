package dev.gdinizds.discordaibot.config;

import dev.gdinizds.discordaibot.application.service.Mdc;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class Resilience {

    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final TimeLimiterRegistry timeLimiters;
    private final BulkheadRegistry bulkheads;
    private final ExecutorService executor;

    public Resilience(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries,
                      TimeLimiterRegistry timeLimiters, BulkheadRegistry bulkheads,
                      ExecutorService executor) {
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.timeLimiters = timeLimiters;
        this.bulkheads = bulkheads;
        this.executor = executor;
    }

    public <T> T call(String instance, Supplier<T> supplier) {
        Callable<T> callable = supplier::get;

        TimeLimiter timeLimiter = timeLimiters.find(instance).orElse(null);
        if (timeLimiter != null) {
            Callable<T> inner = callable;
            callable = () -> timeLimiter.executeFutureSupplier(() -> executor.submit(Mdc.propagate(inner)));
        }
        CircuitBreaker circuitBreaker = circuitBreakers.find(instance).orElse(null);
        if (circuitBreaker != null) callable = CircuitBreaker.decorateCallable(circuitBreaker, unwrapping(callable));
        Retry retry = retries.find(instance).orElse(null);
        if (retry != null) callable = Retry.decorateCallable(retry, callable);
        Bulkhead bulkhead = bulkheads.find(instance).orElse(null);
        if (bulkhead != null) callable = Bulkhead.decorateCallable(bulkhead, callable);

        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalCallException(instance, e);
        }
    }

    public void run(String instance, Runnable runnable) {
        call(instance, () -> {
            runnable.run();
            return null;
        });
    }

    private static <T> Callable<T> unwrapping(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (ExecutionException | CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw e;
            }
        };
    }

    public static class ExternalCallException extends RuntimeException {
        public ExternalCallException(String instance, Throwable cause) {
            super(instance + ": " + cause, cause);
        }
    }
}

