package dev.gdinizds.discordaibot.adapter.out.openrouter;

import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.langchain4j.model.embedding.EmbeddingModel;

public class OpenRouterEmbeddingAdapter implements EmbeddingPort {

    static final String INSTANCE = "openrouter-embedding";

    private final EmbeddingModel model;
    private final Resilience resilience;
    private final String modelName;
    private final int dimensions;

    public OpenRouterEmbeddingAdapter(EmbeddingModel model, Resilience resilience, String modelName, int dimensions) {
        this.model = model;
        this.resilience = resilience;
        this.modelName = modelName;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = resilience.call(INSTANCE, () -> model.embed(text).content().vector());
        if (vector.length != dimensions) {
            throw new IllegalStateException("Expected %d dimensions, got %d".formatted(dimensions, vector.length));
        }
        return vector;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}

