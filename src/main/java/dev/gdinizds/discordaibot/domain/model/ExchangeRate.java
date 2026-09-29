package dev.gdinizds.discordaibot.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRate(
        String from,
        String to,
        String name,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal high,
        BigDecimal low,
        BigDecimal pctChange,
        Instant updatedAt) {}
