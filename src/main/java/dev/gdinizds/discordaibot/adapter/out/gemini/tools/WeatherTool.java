package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.WeatherPort;
import dev.gdinizds.discordaibot.domain.model.Forecast;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class WeatherTool {

    static final String UNAVAILABLE = "Previsão do tempo indisponível no momento.";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR"));

    private final WeatherPort weather;
    private final ToolSupport support;
    private final Duration timeout;

    public WeatherTool(WeatherPort weather, ToolSupport support, Duration timeout) {
        this.weather = weather;
        this.support = support;
        this.timeout = timeout;
    }

    @Tool(name = "weather", value = """
            Condição atual e previsão do tempo de uma cidade. Use quando o usuário perguntar sobre clima, \
            temperatura ou chuva. Não use para clima histórico nem para médias climáticas.""")
    public String weather(@P("nome da cidade, com estado ou país se houver ambiguidade") String city,
                          @P(value = "dias de previsão, de 1 a 7", required = false) Integer days) {
        int n = days == null ? 3 : Math.clamp(days, 1, 7);
        return support.run("weather", timeout, UNAVAILABLE, () -> weather.forecast(city, n)
                .map(WeatherTool::format)
                .orElse("Cidade não encontrada: " + city));
    }

    private static String format(Forecast f) {
        StringBuilder sb = new StringBuilder(f.location()).append('\n');
        if (f.current() != null) {
            sb.append(String.format(Locale.ROOT, "Agora: %s, %.1f °C, umidade %.0f%%, vento %.0f km/h%n",
                    f.current().condition(), f.current().temperature(),
                    f.current().relativeHumidity(), f.current().windSpeed()));
        }
        for (Forecast.Day d : f.days()) {
            sb.append(String.format(Locale.ROOT, "%s: %s, mín %.0f °C, máx %.0f °C, chuva %s%n",
                    DAY.format(d.date()), d.condition(), d.min(), d.max(),
                    d.precipitationProbability() == null ? "?" : d.precipitationProbability() + "%"));
        }
        return sb.toString();
    }
}

