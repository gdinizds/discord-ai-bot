package dev.gdinizds.discordaibot.application.service;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

public record UsageSettings(
        boolean enabled,
        int perUserPerMinute,
        int perUserDailyRequests,
        double perUserDailyUsd,
        double perUserMonthlyUsd,
        double monthlyUsd,
        Map<String, ModelPrice> prices,
        ModelPrice defaultPrice,
        Set<String> exemptUserIds,
        ZoneId zone,
        Messages messages) {

    public record ModelPrice(double inputUsdPerMillion, double outputUsdPerMillion) {}

    public record Messages(String burst, String userDaily, String userMonthly, String monthly) {}

    public UsageSettings {
        prices = prices == null ? Map.of() : Map.copyOf(prices);
        exemptUserIds = exemptUserIds == null ? Set.of() : Set.copyOf(exemptUserIds);
    }

    public ModelPrice priceOf(String model) {
        return model == null ? defaultPrice : prices.getOrDefault(model, defaultPrice);
    }
}
