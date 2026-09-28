package dev.gdinizds.discordaibot.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

public class DeterministicEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    public static float[] vectorOf(String text) {
        var random = new Random(seed(text));
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) random.nextGaussian();
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSIONS; i++) vector[i] *= scale;
        return vector;
    }

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(Embedding.from(vectorOf(text)));
    }

    @Override
    public Response<Embedding> embed(TextSegment segment) {
        return embed(segment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        return Response.from(segments.stream().map(s -> Embedding.from(vectorOf(s.text()))).toList());
    }

    @Override
    public int dimension() {
        return DIMENSIONS;
    }

    private static long seed(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

