package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;

public class UsageService {

    public enum Decision { ALLOWED, USER_DAILY_LIMIT, MONTHLY_LIMIT }

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsagePort usage;
    private final UsageSettings settings;
    private final Clock clock;

    public UsageService(UsagePort usage, UsageSettings settings, Clock clock) {
        this.usage = usage;
        this.settings = settings;
        this.clock = clock;
    }

    public static UsageService unlimited(Clock clock) {
        return new UsageService(null, new UsageSettings(false, 0, 0, 0, null,
                new UsageSettings.ModelPrice(0, 0), null, clock.getZone(), "", ""), clock);
    }

    public Decision check(String userId) {
        if (!settings.enabled() || settings.exemptUserIds().contains(userId)) return Decision.ALLOWED;
        try {
            LocalDate today = today();
            long month = usage.costMicroUsdBetween(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1));
            if (month >= micros(settings.monthlyUsd())) return Decision.MONTHLY_LIMIT;
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

    public void record(String userId, AiAnswer answer) {
        if (!settings.enabled()) return;
        try {
            usage.add(userId, today(), answer.usage(), cost(answer.model(), answer.usage()));
        } catch (RuntimeException e) {
            log.warn("Could not record usage: {}", e.toString());
        }
    }

    public String message(Decision decision) {
        return decision == Decision.MONTHLY_LIMIT ? settings.monthlyLimitText() : settings.userLimitText();
    }

    long cost(String model, TokenUsage tokens) {
        UsageSettings.ModelPrice price = settings.priceOf(model);
        return Math.round(tokens.input() * price.inputUsdPerMillion() + tokens.output() * price.outputUsdPerMillion());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(settings.zone()));
    }

    private static long micros(double usd) {
        return Math.round(usd * 1_000_000);
    }
}
