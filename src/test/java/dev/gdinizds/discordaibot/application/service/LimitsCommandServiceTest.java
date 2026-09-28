package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.LimitsCommand;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.gdinizds.discordaibot.support.RecordingReplyPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LimitsCommandServiceTest {

    private static final LimitsCommand COMMAND = new LimitsCommand("cid-1", "10", "42", "tok");

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneOffset.UTC);
    private final RecordingReplyPublisher publisher = new RecordingReplyPublisher();
    private final Set<String> processed = new HashSet<>();

    @Test
    void showsTheUserAndGlobalUsageEphemerally() {
        service(usage(true, Set.of(), false)).handle(COMMAND);

        assertThat(publisher.kinds()).containsExactly(RecordingReplyPublisher.Kind.EPHEMERAL);
        String text = publisher.last().chunks().getFirst();
        assertThat(text)
                .contains("Hoje: US$ 1,25 de US$ 5,00 · 12 de 150 perguntas")
                .contains("Este mês: US$ 3,40 de US$ 10,00")
                .contains("até 6 perguntas por minuto")
                .contains("US$ 7,00 de US$ 20,00 (35%)")
                .contains("America/Sao_Paulo");
    }

    @Test
    void exemptUserSeesNoPersonalLimit() {
        service(usage(true, Set.of("42"), false)).handle(COMMAND);

        assertThat(publisher.last().chunks().getFirst())
                .contains("Você não tem limite pessoal.")
                .doesNotContain("Hoje:")
                .contains("US$ 7,00 de US$ 20,00");
    }

    @Test
    void disabledLimitsSaySo() {
        service(usage(false, Set.of(), false)).handle(COMMAND);

        assertThat(publisher.last().chunks()).containsExactly("Os limites de uso da IA estão desligados.");
    }

    @Test
    void storageFailureAnswersWithTheFallback() {
        service(usage(true, Set.of(), true)).handle(COMMAND);

        assertThat(publisher.last().chunks()).containsExactly("fallback");
    }

    @Test
    void duplicateCommandIsAnsweredOnce() {
        var service = service(usage(true, Set.of(), false));
        service.handle(COMMAND);
        service.handle(COMMAND);

        assertThat(publisher.calls).hasSize(1);
    }

    private LimitsCommandService service(UsageService usage) {
        return new LimitsCommandService(usage, processed::add, publisher, "fallback");
    }

    private UsageService usage(boolean enabled, Set<String> exempt, boolean fail) {
        UsagePort port = new UsagePort() {
            @Override
            public UserDay userDay(String userId, LocalDate day) {
                if (fail) throw new IllegalStateException("db down");
                return new UserDay(12, 1_250_000);
            }

            @Override
            public long costMicroUsdBetween(LocalDate from, LocalDate to) {
                return 7_000_000;
            }

            @Override
            public long userCostMicroUsdBetween(String userId, LocalDate from, LocalDate to) {
                return 3_400_000;
            }

            @Override
            public void add(String userId, LocalDate day, TokenUsage usage, long cost) {}
        };
        return new UsageService(port, new UsageSettings(enabled, 6, 150, 5.00, 10.00, 20.00, Map.of(),
                new UsageSettings.ModelPrice(1.50, 9.00), exempt, ZoneId.of("America/Sao_Paulo"),
                new UsageSettings.Messages("", "", "", "")), clock);
    }
}
