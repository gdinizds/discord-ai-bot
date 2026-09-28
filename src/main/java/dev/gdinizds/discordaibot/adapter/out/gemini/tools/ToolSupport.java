package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.service.Mdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class ToolSupport {

    private static final Logger log = LoggerFactory.getLogger(ToolSupport.class);
    private static final String TRUNCATED = "\n[resultado cortado]";

    private final MetricsPort metrics;
    private final ExecutorService executor;
    private final int maxResultChars;

    public ToolSupport(MetricsPort metrics, ExecutorService executor, int maxResultChars) {
        this.metrics = metrics;
        this.executor = executor;
        this.maxResultChars = maxResultChars;
    }

    public String run(String tool, Duration timeout, String unavailable, Supplier<String> call) {
        Future<String> future = executor.submit(Mdc.propagate(call::get));
        try {
            String result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            metrics.toolCall(tool, true);
            return truncate(result == null ? "" : result);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Tool {} timed out after {}", tool, timeout);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Tool {} failed: {}", tool, cause.toString());
        }
        metrics.toolCall(tool, false);
        return unavailable;
    }

    String truncate(String text) {
        if (text.length() <= maxResultChars) return text;
        int end = maxResultChars - TRUNCATED.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + TRUNCATED;
    }

    static String cut(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").strip();
        if (clean.length() <= max) return clean;
        int end = max - 1;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        return clean.substring(0, end) + "…";
    }
}

