package dev.gdinizds.discordaibot.adapter.out.gemini;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

