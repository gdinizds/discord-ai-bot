package dev.gdinizds.discordaibot.domain.model;

import java.time.LocalDate;
import java.util.List;

public record Forecast(String location, Current current, List<Day> days) {

    public record Current(double temperature, double relativeHumidity, double windSpeed, String condition) {}

    public record Day(LocalDate date, double min, double max, Integer precipitationProbability, String condition) {}
}

