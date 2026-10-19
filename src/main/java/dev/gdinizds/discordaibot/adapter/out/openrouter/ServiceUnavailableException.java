package dev.gdinizds.discordaibot.adapter.out.openrouter;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

