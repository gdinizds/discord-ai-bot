package dev.gdinizds.discordaibot.application.port.out;

public interface EmbeddingPort {

    float[] embed(String text);

    String modelName();
}

