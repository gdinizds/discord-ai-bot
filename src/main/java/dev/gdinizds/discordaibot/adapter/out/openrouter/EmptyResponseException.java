package dev.gdinizds.discordaibot.adapter.out.openrouter;

public class EmptyResponseException extends RuntimeException {
    public EmptyResponseException(String message) {
        super(message);
    }
}
