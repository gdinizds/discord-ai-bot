package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ExchangeRate;

import java.util.Optional;

public interface ExchangeRatePort {

    Optional<ExchangeRate> latest(String from, String to);
}
