package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.domain.model.TriggerType;

import java.time.Duration;

public interface MetricsPort {

    enum Outcome { ANSWERED, FALLBACK, IGNORED, DUPLICATE, LIMITED }

    enum Stage { CONTEXT, LLM, PUBLISH, TOTAL }

    enum MemoryWrite { INSERT, UPDATE, REJECTED, EVICTED }

    void request(TriggerType trigger, Outcome outcome);

    void latency(Stage stage, Duration duration);

    void tokens(TokenUsage usage);

    void toolCall(String tool, boolean success);

    void memoryWrite(MemoryWrite action);

    void replyChunks(int count);

    MetricsPort NOOP = new MetricsPort() {
        @Override public void request(TriggerType trigger, Outcome outcome) {}
        @Override public void latency(Stage stage, Duration duration) {}
        @Override public void tokens(TokenUsage usage) {}
        @Override public void toolCall(String tool, boolean success) {}
        @Override public void memoryWrite(MemoryWrite action) {}
        @Override public void replyChunks(int count) {}
    };
}

