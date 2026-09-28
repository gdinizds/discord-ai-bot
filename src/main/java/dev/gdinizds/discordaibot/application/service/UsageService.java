package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class UsageService {

    public enum Decision { ALLOWED, USER_BURST_LIMIT, USER_DAILY_LIMIT, USER_MONTHLY_LIMIT, MONTHLY_LIMIT }

    public record Report(boolean enabled, boolean exempt,
                         int userDayRequests, int userDayRequestLimit,
                         long userDayMicroUsd, double userDayLimitUsd,
                         long userMonthMicroUsd, double userMonthLimitUsd,
                         long monthMicroUsd, double monthLimitUsd,
                         int perMinute, String zone) {}

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    private static final Duration BURST_WINDOW = Duration.ofMinutes(1);

    private final UsagePort usage;
    private final UsageSettings settings;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public UsageService(UsagePort usage, UsageSettings settings, Clock clock) {
        this.usage = usage;
        this.settings = settings;
        this.clock = clock;
    }

    public static UsageService unlimited(Clock clock) {
        return new UsageService(null, new UsageSettings(false, 0, 0, 0, 0, 0, null,
                new UsageSettings.ModelPrice(0, 0), null, clock.getZone(),
                new UsageSettings.Messages("", "", "", "")), clock);
    }

    public Decision check(String userId) {
        if (!settings.enabled() || settings.exemptUserIds().contains(userId)) return Decision.ALLOWED;
        if (!takeBurstSlot(userId)) return Decision.USER_BURST_LIMIT;
        try {
            LocalDate today = today();
            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate nextMonth = monthStart.plusMonths(1);
            if (usage.costMicroUsdBetween(monthStart, nextMonth) >= micros(settings.monthlyUsd())) {
                return Decision.MONTHLY_LIMIT;
            }
            if (usage.userCostMicroUsdBetween(userId, monthStart, nextMonth) >= micros(settings.perUserMonthlyUsd())) {
                return Decision.USER_MONTHLY_LIMIT;
            }
            UsagePort.UserDay day = usage.userDay(userId, today);
            if (day.requests() >= settings.perUserDailyRequests()
                    || day.costMicroUsd() >= micros(settings.perUserDailyUsd())) {
                return Decision.USER_DAILY_LIMIT;
            }
            return Decision.ALLOWED;
        } catch (RuntimeException e) {
            log.warn("Usage check unavailable, allowing request: {}", e.toString());
            return Decision.ALLOWED;
        }
    }

    public Report report(String userId) {
        if (!settings.enabled()) {
            return new Report(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, settings.zone().getId());
        }
        LocalDate today = today();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        UsagePort.UserDay day = usage.userDay(userId, today);
        return new Report(true, settings.exemptUserIds().contains(userId),
                day.requests(), settings.perUserDailyRequests(),
                day.costMicroUsd(), settings.perUserDailyUsd(),
                usage.userCostMicroUsdBetween(userId, monthStart, nextMonth), settings.perUserMonthlyUsd(),
                usage.costMicroUsdBetween(monthStart, nextMonth), settings.monthlyUsd(),
                settings.perUserPerMinute(), settings.zone().getId());
    }

    public void record(String userId, AiAnswer answer) {
        if (!settings.enabled()) return;
        try {
            usage.add(userId, today(), answer.usage(), cost(answer.model(), answer.usage()));
        } catch (RuntimeException e) {
            log.warn("Could not record usage: {}", e.toString());
        }
    }

    public String message(Decision decision) {
        var messages = settings.messages();
        return switch (decision) {
            case USER_BURST_LIMIT -> messages.burst();
            case USER_DAILY_LIMIT -> messages.userDaily();
            case USER_MONTHLY_LIMIT -> messages.userMonthly();
            case MONTHLY_LIMIT -> messages.monthly();
            case ALLOWED -> "";
        };
    }

    long cost(String model, TokenUsage tokens) {
        UsageSettings.ModelPrice price = settings.priceOf(model);
        return Math.round(tokens.input() * price.inputUsdPerMillion() + tokens.output() * price.outputUsdPerMillion());
    }

    private boolean takeBurstSlot(String userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(BURST_WINDOW);
        Deque<Instant> times = recent.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) times.pollFirst();
            if (times.size() >= settings.perUserPerMinute()) return false;
            times.addLast(now);
            return true;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(settings.zone()));
    }

    private static long micros(double usd) {
        return Math.round(usd * 1_000_000);
    }
}
