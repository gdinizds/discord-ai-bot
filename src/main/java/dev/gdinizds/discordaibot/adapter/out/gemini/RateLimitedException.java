package dev.gdinizds.discordaibot.adapter.out.gemini;

public class RateLimitedException extends RuntimeException {
    public RateLimitedException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

