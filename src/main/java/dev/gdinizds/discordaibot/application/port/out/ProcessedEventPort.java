package dev.gdinizds.discordaibot.application.port.out;

public interface ProcessedEventPort {

    boolean tryAcquire(String correlationId);
}

