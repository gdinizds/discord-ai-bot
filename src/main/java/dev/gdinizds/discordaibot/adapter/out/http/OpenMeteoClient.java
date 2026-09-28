package dev.gdinizds.discordaibot.adapter.out.http;

import dev.gdinizds.discordaibot.application.port.out.WeatherPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.Forecast;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public class OpenMeteoClient implements WeatherPort {

    static final String INSTANCE = "open-meteo";

    private static final Map<Integer, String> WMO = Map.ofEntries(
            Map.entry(0, "céu limpo"),
            Map.entry(1, "predominantemente limpo"),
            Map.entry(2, "parcialmente nublado"),
            Map.entry(3, "nublado"),
            Map.entry(45, "nevoeiro"),
            Map.entry(48, "nevoeiro com geada"),
            Map.entry(51, "garoa fraca"),
            Map.entry(53, "garoa moderada"),
            Map.entry(55, "garoa forte"),
            Map.entry(56, "garoa congelante fraca"),
            Map.entry(57, "garoa congelante forte"),
            Map.entry(61, "chuva fraca"),
            Map.entry(63, "chuva moderada"),
            Map.entry(65, "chuva forte"),
            Map.entry(66, "chuva congelante fraca"),
            Map.entry(67, "chuva congelante forte"),
            Map.entry(71, "neve fraca"),
            Map.entry(73, "neve moderada"),
            Map.entry(75, "neve forte"),
            Map.entry(77, "grãos de neve"),
            Map.entry(80, "pancadas de chuva fracas"),
            Map.entry(81, "pancadas de chuva moderadas"),
            Map.entry(82, "pancadas de chuva fortes"),
            Map.entry(85, "pancadas de neve fracas"),
            Map.entry(86, "pancadas de neve fortes"),
            Map.entry(95, "trovoada"),
            Map.entry(96, "trovoada com granizo fraco"),
            Map.entry(99, "trovoada com granizo forte"));

    private final RestClient geocoding;
    private final RestClient forecast;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public OpenMeteoClient(RestClient.Builder builder, ObjectMapper objectMapper, Resilience resilience,
                           String geocodingUrl, String forecastUrl, Duration timeout) {
        this.geocoding = RestClients.create(builder, geocodingUrl, timeout);
        this.forecast = RestClients.create(builder, forecastUrl, timeout);
        this.objectMapper = objectMapper;
        this.resilience = resilience;
    }

    @Override
    public Optional<Forecast> forecast(String city, int days) {
        return resilience.call(INSTANCE, () -> geocode(city).map(place -> fetchForecast(place, days)));
    }

    private Optional<Place> geocode(String city) {
        String body = geocoding.get()
                .uri(uri -> uri.path("/v1/search")
                        .queryParam("name", city)
                        .queryParam("count", 1)
                        .queryParam("language", "pt")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode first = objectMapper.readTree(body).path("results").path(0);
        if (first.isMissingNode()) return Optional.empty();

        StringJoiner name = new StringJoiner(", ");
        for (String field : List.of("name", "admin1", "country")) {
            String value = first.path(field).asString("");
            if (!value.isBlank()) name.add(value);
        }
        return Optional.of(new Place(name.toString(), first.path("latitude").asDouble(), first.path("longitude").asDouble()));
    }

    private Forecast fetchForecast(Place place, int days) {
        String body = forecast.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", place.latitude())
                        .queryParam("longitude", place.longitude())
                        .queryParam("current", "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code")
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", days)
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(body);

        JsonNode current = root.path("current");
        Forecast.Current now = current.isMissingNode() ? null : new Forecast.Current(
                current.path("temperature_2m").asDouble(),
                current.path("relative_humidity_2m").asDouble(),
                current.path("wind_speed_10m").asDouble(),
                condition(current.path("weather_code")));

        JsonNode daily = root.path("daily");
        List<Forecast.Day> list = new ArrayList<>();
        JsonNode dates = daily.path("time");
        for (int i = 0; i < dates.size(); i++) {
            JsonNode precipitation = daily.path("precipitation_probability_max").path(i);
            list.add(new Forecast.Day(
                    LocalDate.parse(dates.path(i).asString()),
                    daily.path("temperature_2m_min").path(i).asDouble(),
                    daily.path("temperature_2m_max").path(i).asDouble(),
                    precipitation.isNumber() ? precipitation.asInt() : null,
                    condition(daily.path("weather_code").path(i))));
        }
        return new Forecast(place.name(), now, list);
    }

    static String condition(JsonNode code) {
        return code.isNumber() ? WMO.getOrDefault(code.asInt(), "condição desconhecida") : "condição desconhecida";
    }

    private record Place(String name, double latitude, double longitude) {}
}

