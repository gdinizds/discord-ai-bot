package dev.gdinizds.discordaibot.adapter.out.openrouter;

public class RateLimitedException extends RuntimeException {
    public RateLimitedException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

