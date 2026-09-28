package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UsageServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T02:30:00Z"), ZoneOffset.UTC);
    private final FakeUsage port = new FakeUsage();

    @Test
    void allowsUnderEveryLimit() {
        port.day = new UsagePort.UserDay(3, 20_000);
        port.month = 1_000_000;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void blocksAfterTheDailyRequestCount() {
        port.day = new UsagePort.UserDay(25, 0);

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.USER_DAILY_LIMIT);
    }

    @Test
    void blocksAfterTheDailyCost() {
        port.day = new UsagePort.UserDay(1, 100_000);

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.USER_DAILY_LIMIT);
    }

    @Test
    void monthlyBudgetWinsOverTheUserLimit() {
        port.month = 8_000_000;
        port.day = new UsagePort.UserDay(25, 0);

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.MONTHLY_LIMIT);
    }

    @Test
    void usesTheLocalDayAndMonthOfTheConfiguredZone() {
        service(true, Set.of()).check("42");

        assertThat(port.dayQueried).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(port.monthQueried).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
    }

    @Test
    void exemptUsersAndDisabledLimitsAreNeverBlocked() {
        port.month = 99_000_000;

        assertThat(service(true, Set.of("42")).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
        assertThat(service(false, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void storageFailureDoesNotBlockTheUser() {
        port.fail = true;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void costUsesTheModelPriceAndFallsBackToTheDefaultPrice() {
        var service = service(true, Set.of());
        var tokens = new TokenUsage(1_000, 200);

        assertThat(service.cost("gemini-3.5-flash-lite", tokens)).isEqualTo(800);
        assertThat(service.cost("modelo-desconhecido", tokens)).isEqualTo(3_300);
        assertThat(service.cost(null, tokens)).isEqualTo(3_300);
    }

    @Test
    void recordStoresTokensAndCostForTheUserDay() {
        service(true, Set.of()).record("42",
                new AiAnswer("ok", List.of(), new TokenUsage(1_000, 200), false, List.of(), "gemini-3.5-flash-lite"));

        assertThat(port.added).containsExactly("42/2026-09-30/1000/200/800");
    }

    private UsageService service(boolean enabled, Set<String> exempt) {
        return new UsageService(port, new UsageSettings(enabled, 25, 0.10, 8.00,
                Map.of("gemini-3.5-flash-lite", new UsageSettings.ModelPrice(0.30, 2.50)),
                new UsageSettings.ModelPrice(1.50, 9.00), exempt, ZoneId.of("America/Sao_Paulo"),
                "limite diário", "limite mensal"), clock);
    }

    private static class FakeUsage implements UsagePort {
        UserDay day = UserDay.ZERO;
        long month;
        boolean fail;
        LocalDate dayQueried;
        List<LocalDate> monthQueried = new ArrayList<>();
        List<String> added = new ArrayList<>();

        @Override
        public UserDay userDay(String userId, LocalDate date) {
            if (fail) throw new IllegalStateException("db down");
            dayQueried = date;
            return day;
        }

        @Override
        public long costMicroUsdBetween(LocalDate from, LocalDate to) {
            if (fail) throw new IllegalStateException("db down");
            monthQueried.add(from);
            monthQueried.add(to);
            return month;
        }

        @Override
        public void add(String userId, LocalDate date, TokenUsage tokens, long cost) {
            added.add(userId + "/" + date + "/" + tokens.input() + "/" + tokens.output() + "/" + cost);
        }
    }
}
