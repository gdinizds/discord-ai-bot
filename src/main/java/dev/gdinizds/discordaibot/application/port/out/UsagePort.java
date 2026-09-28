package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.TokenUsage;

import java.time.LocalDate;

public interface UsagePort {

    record UserDay(int requests, long costMicroUsd) {
        public static final UserDay ZERO = new UserDay(0, 0);
    }

    UserDay userDay(String userId, LocalDate day);

    long costMicroUsdBetween(LocalDate from, LocalDate toExclusive);

    long userCostMicroUsdBetween(String userId, LocalDate from, LocalDate toExclusive);

    void add(String userId, LocalDate day, TokenUsage usage, long costMicroUsd);
}
