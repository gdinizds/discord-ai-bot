package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.Forecast;

import java.util.Optional;

public interface WeatherPort {

    Optional<Forecast> forecast(String city, int days);
}

