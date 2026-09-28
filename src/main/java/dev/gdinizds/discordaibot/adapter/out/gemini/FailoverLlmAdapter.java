package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailoverLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(FailoverLlmAdapter.class);

    private final LlmPort primary;
    private final LlmPort fallback;
    private final String fallbackModel;

    public FailoverLlmAdapter(LlmPort primary, LlmPort fallback, String fallbackModel) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public AiAnswer answer(LlmPrompt prompt) {
        try {
            return primary.answer(prompt);
        } catch (RuntimeException e) {
            if (!isCapacityProblem(e)) throw e;
            log.warn("Primary model unavailable ({}), answering with {}", e.getClass().getSimpleName(), fallbackModel);
            return fallback.answer(prompt);
        }
    }

    static boolean isCapacityProblem(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ServiceUnavailableException
                    || t instanceof RateLimitedException
                    || t instanceof CallNotPermittedException) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
