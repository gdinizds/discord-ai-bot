package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
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

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T02:30:00Z"));
    private final FakeUsage port = new FakeUsage();

    @Test
    void allowsUnderEveryLimit() {
        port.day = new UsagePort.UserDay(3, 1_000_000);
        port.userMonth = 4_000_000;
        port.month = 15_000_000;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void blocksAfterTheDailyCost() {
        port.day = new UsagePort.UserDay(1, 5_000_000);

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.USER_DAILY_LIMIT);
    }

    @Test
    void blocksAfterTheDailyRequestCount() {
        port.day = new UsagePort.UserDay(150, 0);

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.USER_DAILY_LIMIT);
    }

    @Test
    void blocksAfterTheUserMonthlyCost() {
        port.userMonth = 10_000_000;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.USER_MONTHLY_LIMIT);
    }

    @Test
    void globalMonthlyBudgetWinsOverUserLimits() {
        port.month = 20_000_000;
        port.userMonth = 10_000_000;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.MONTHLY_LIMIT);
    }

    @Test
    void burstLimitResetsAfterAMinute() {
        var service = service(true, Set.of());
        for (int i = 0; i < 6; i++) assertThat(service.check("42")).isEqualTo(UsageService.Decision.ALLOWED);

        assertThat(service.check("42")).isEqualTo(UsageService.Decision.USER_BURST_LIMIT);
        assertThat(service.check("43")).isEqualTo(UsageService.Decision.ALLOWED);

        clock.advance(Duration.ofSeconds(61));
        assertThat(service.check("42")).isEqualTo(UsageService.Decision.ALLOWED);
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
        var exempt = service(true, Set.of("42"));
        for (int i = 0; i < 10; i++) assertThat(exempt.check("42")).isEqualTo(UsageService.Decision.ALLOWED);

        assertThat(service(false, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void storageFailureDoesNotBlockTheUser() {
        port.fail = true;

        assertThat(service(true, Set.of()).check("42")).isEqualTo(UsageService.Decision.ALLOWED);
    }

    @Test
    void idleUsersStopBeingTrackedForBurstLimits() {
        var service = service(true, Set.of());
        for (int i = 0; i < 1100; i++) service.check("user-" + i);
        assertThat(service.trackedUsers()).isEqualTo(1100);

        clock.advance(Duration.ofSeconds(61));
        service.check("42");

        assertThat(service.trackedUsers()).isEqualTo(1);
    }

    @Test
    void eachDecisionHasItsOwnMessage() {
        var service = service(true, Set.of());

        assertThat(service.message(UsageService.Decision.USER_BURST_LIMIT)).isEqualTo("rajada");
        assertThat(service.message(UsageService.Decision.USER_DAILY_LIMIT)).isEqualTo("diário");
        assertThat(service.message(UsageService.Decision.USER_MONTHLY_LIMIT)).isEqualTo("mensal do usuário");
        assertThat(service.message(UsageService.Decision.MONTHLY_LIMIT)).isEqualTo("mensal global");
    }

    @Test
    void reportShowsUsageAgainstEachLimit() {
        port.day = new UsagePort.UserDay(12, 1_250_000);
        port.userMonth = 3_400_000;
        port.month = 7_000_000;

        var report = service(true, Set.of()).report("42");

        assertThat(report.enabled()).isTrue();
        assertThat(report.exempt()).isFalse();
        assertThat(report.userDayRequests()).isEqualTo(12);
        assertThat(report.userDayMicroUsd()).isEqualTo(1_250_000);
        assertThat(report.userMonthMicroUsd()).isEqualTo(3_400_000);
        assertThat(report.monthMicroUsd()).isEqualTo(7_000_000);
        assertThat(report.monthLimitUsd()).isEqualTo(20.00);
        assertThat(port.dayQueried).isEqualTo(LocalDate.of(2026, 9, 30));
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
        return new UsageService(port, new UsageSettings(enabled, 6, 150, 5.00, 10.00, 20.00,
                Map.of("gemini-3.5-flash-lite", new UsageSettings.ModelPrice(0.30, 2.50)),
                new UsageSettings.ModelPrice(1.50, 9.00), exempt, ZoneId.of("America/Sao_Paulo"),
                new UsageSettings.Messages("rajada", "diário", "mensal do usuário", "mensal global")), clock);
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    private static class FakeUsage implements UsagePort {
        UserDay day = UserDay.ZERO;
        long month;
        long userMonth;
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
        public long userCostMicroUsdBetween(String userId, LocalDate from, LocalDate to) {
            if (fail) throw new IllegalStateException("db down");
            return userMonth;
        }

        @Override
        public void add(String userId, LocalDate date, TokenUsage tokens, long cost) {
            added.add(userId + "/" + date + "/" + tokens.input() + "/" + tokens.output() + "/" + cost);
        }
    }
}
