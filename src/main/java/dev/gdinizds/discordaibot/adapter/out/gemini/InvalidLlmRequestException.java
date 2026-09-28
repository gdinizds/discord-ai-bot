package dev.gdinizds.discordaibot.adapter.out.gemini;

public class InvalidLlmRequestException extends RuntimeException {
    public InvalidLlmRequestException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

