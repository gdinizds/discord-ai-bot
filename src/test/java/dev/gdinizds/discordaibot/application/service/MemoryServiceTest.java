package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.EmbeddingPort;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.application.port.out.UserMemoryPort.NewMemory;
import dev.gdinizds.discordaibot.domain.model.MemorySaveResult;
import dev.gdinizds.discordaibot.support.DeterministicEmbeddingModel;
import dev.gdinizds.discordaibot.support.InMemoryUserMemory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryServiceTest {

    private static final String GUILD = "1";
    private static final String USER = "2";

    private final InMemoryUserMemory memories = new InMemoryUserMemory();
    private final Map<String, float[]> vectors = new HashMap<>();
    private final EmbeddingPort embedding = new EmbeddingPort() {
        @Override
        public float[] embed(String text) {
            return vectors.getOrDefault(text, DeterministicEmbeddingModel.vectorOf(text));
        }

        @Override
        public String modelName() {
            return "gemini-embedding-001";
        }
    };
    private final MemoryService service = new MemoryService(memories, embedding, MetricsPort.NOOP,
            new MemorySettings(500, 0.08, 200));

    @Test
    void nearDuplicateBelowThresholdReplacesTheOldFact() {
        float[] base = DeterministicEmbeddingModel.vectorOf("Usa Java 21");
        vectors.put("Usa Java 21", base);
        vectors.put("Usa Java 25", nudge(base, 0.05));

        assertThat(service.save(GUILD, USER, "Usa Java 21", "SKILL", "c1")).isEqualTo(MemorySaveResult.INSERTED);
        assertThat(service.save(GUILD, USER, "Usa Java 25", "SKILL", "c2")).isEqualTo(MemorySaveResult.UPDATED);

        assertThat(service.list(GUILD, USER)).singleElement()
                .satisfies(m -> assertThat(m.content()).isEqualTo("Usa Java 25"));
    }

    @Test
    void distinctFactAboveThresholdIsInserted() {
        float[] base = DeterministicEmbeddingModel.vectorOf("Prefere respostas curtas");
        vectors.put("Prefere respostas curtas", base);
        vectors.put("Prefere respostas longas", nudge(base, 0.12));

        service.save(GUILD, USER, "Prefere respostas curtas", "PREFERENCE", "c1");
        assertThat(service.save(GUILD, USER, "Prefere respostas longas", "PREFERENCE", "c2"))
                .isEqualTo(MemorySaveResult.INSERTED);
        assertThat(service.list(GUILD, USER)).hasSize(2);
    }

    @Test
    void the201stMemoryEvictsTheLeastRecentlyUsed() {
        for (int i = 0; i < 200; i++) {
            memories.insert(new NewMemory(GUILD, USER, "fato " + i, "FACT", "h" + i,
                    DeterministicEmbeddingModel.vectorOf("fato " + i), "m", null));
        }
        memories.touch(java.util.List.of(1L));

        assertThat(service.save(GUILD, USER, "fato novo", "FACT", "c")).isEqualTo(MemorySaveResult.INSERTED);

        assertThat(memories.count(GUILD, USER)).isEqualTo(200);
        assertThat(memories.evicted).containsExactly(2L);
        assertThat(memories.row(1L)).isNotNull();
    }

    @Test
    void refusesCpfAndCardNumbers() {
        assertThat(service.save(GUILD, USER, "Meu CPF é 123.456.789-09", "FACT", "c"))
                .isEqualTo(MemorySaveResult.REJECTED_SENSITIVE);
        assertThat(service.save(GUILD, USER, "Meu cartão é 4111 1111 1111 1111", "FACT", "c"))
                .isEqualTo(MemorySaveResult.REJECTED_SENSITIVE);
        assertThat(service.list(GUILD, USER)).isEmpty();
    }

    @Test
    void refusesTooLongContentAndUnknownCategory() {
        assertThat(service.save(GUILD, USER, "x".repeat(501), "FACT", "c")).isEqualTo(MemorySaveResult.REJECTED_INVALID);
        assertThat(service.save(GUILD, USER, "fato", "SEGREDO", "c")).isEqualTo(MemorySaveResult.REJECTED_INVALID);
    }

    @Test
    void forgetIsRestrictedToTheOwner() {
        service.save(GUILD, USER, "Mora em Recife", "CONTEXT", "c");
        long id = service.list(GUILD, USER).getFirst().id();

        assertThat(service.forget(GUILD, "outro-usuario", id)).isFalse();
        assertThat(service.forget("outra-guild", USER, id)).isFalse();
        assertThat(service.forget(GUILD, USER, id)).isTrue();
    }

    private static float[] nudge(float[] base, double targetDistance) {
        float[] orthogonal = DeterministicEmbeddingModel.vectorOf("ortogonal");
        double dot = 0;
        for (int i = 0; i < base.length; i++) dot += base[i] * orthogonal[i];
        double norm = 0;
        float[] o = new float[base.length];
        for (int i = 0; i < base.length; i++) {
            o[i] = (float) (orthogonal[i] - dot * base[i]);
            norm += o[i] * o[i];
        }
        double cos = 1 - targetDistance;
        double sin = Math.sqrt(1 - cos * cos);
        float[] result = new float[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = (float) (cos * base[i] + sin * o[i] / Math.sqrt(norm));
        }
        return result;
    }
}

