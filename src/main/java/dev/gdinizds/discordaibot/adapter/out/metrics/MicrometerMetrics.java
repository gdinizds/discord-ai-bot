package dev.gdinizds.discordaibot.adapter.out.metrics;

import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class MicrometerMetrics implements MetricsPort {

    private final MeterRegistry registry;
    private final DistributionSummary replyChunks;

    public MicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.replyChunks = DistributionSummary.builder("discord.aibot.reply.chunks").register(registry);
    }

    @Override
    public void request(TriggerType trigger, Outcome outcome) {
        registry.counter("discord.aibot.requests", "trigger", tag(trigger), "outcome", tag(outcome)).increment();
    }

    @Override
    public void latency(Stage stage, Duration duration) {
        registry.timer("discord.aibot.latency", "stage", tag(stage)).record(duration);
    }

    @Override
    public void tokens(TokenUsage usage) {
        registry.counter("discord.aibot.llm.tokens", "direction", "input").increment(usage.input());
        registry.counter("discord.aibot.llm.tokens", "direction", "output").increment(usage.output());
    }

    @Override
    public void toolCall(String tool, boolean success) {
        registry.counter("discord.aibot.tool.calls", "tool", tool, "outcome", success ? "success" : "failure")
                .increment();
    }

    @Override
    public void memoryWrite(MemoryWrite action) {
        registry.counter("discord.aibot.memory.writes", "action", tag(action)).increment();
    }

    @Override
    public void replyChunks(int count) {
        replyChunks.record(count);
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

