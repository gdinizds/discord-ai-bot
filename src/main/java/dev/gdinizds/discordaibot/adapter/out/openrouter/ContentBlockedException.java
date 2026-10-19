package dev.gdinizds.discordaibot.adapter.out.openrouter;

public class ContentBlockedException extends RuntimeException {
    public ContentBlockedException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

