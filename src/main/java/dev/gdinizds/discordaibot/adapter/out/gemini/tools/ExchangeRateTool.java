package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.ExchangeRatePort;
import dev.gdinizds.discordaibot.domain.model.ExchangeRate;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ExchangeRateTool {

    static final String UNAVAILABLE = "Cotação indisponível no momento.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExchangeRatePort rates;
    private final ToolSupport support;
    private final Duration timeout;
    private final ZoneId zone;

    public ExchangeRateTool(ExchangeRatePort rates, ToolSupport support, Duration timeout, ZoneId zone) {
        this.rates = rates;
        this.support = support;
        this.timeout = timeout;
        this.zone = zone;
    }

    @Tool(name = "exchange_rate", value = """
            Cotação atual de moedas e criptomoedas e conversão de valores. Use para dólar, euro, bitcoin e \
            outras. Informe códigos como USD, EUR, BRL, GBP, JPY, ARS, CAD, BTC, ETH. Não use para preço de \
            produtos.""")
    public String exchangeRate(@P("moeda de origem, código como USD") String from,
                               @P(value = "moeda de destino, padrão BRL", required = false) String to,
                               @P(value = "valor a converter na moeda de origem", required = false) Double amount) {
        String target = to == null || to.isBlank() ? "BRL" : to;
        return support.run("exchange_rate", timeout, UNAVAILABLE, () -> rates.latest(from, target)
                .map(rate -> format(rate, amount))
                .orElse("Par de moedas não encontrado: " + from + "/" + target + ". Use códigos como USD, EUR, BTC."));
    }

    String format(ExchangeRate rate, Double amount) {
        StringBuilder sb = new StringBuilder();
        sb.append(rate.name() == null || rate.name().isBlank() ? rate.from() + "/" + rate.to() : rate.name()).append('\n');
        sb.append("1 ").append(rate.from()).append(" = ").append(plain(rate.bid())).append(' ').append(rate.to())
                .append(" (compra)");
        if (rate.ask() != null) sb.append(", ").append(plain(rate.ask())).append(" (venda)");
        sb.append('\n');
        if (rate.pctChange() != null) sb.append("Variação no dia: ").append(plain(rate.pctChange())).append("%");
        if (rate.low() != null && rate.high() != null) {
            sb.append(" (mín ").append(plain(rate.low())).append(", máx ").append(plain(rate.high())).append(')');
        }
        sb.append('\n');
        if (rate.updatedAt() != null) {
            sb.append("Atualizado em ").append(TIME.format(rate.updatedAt().atZone(zone)))
                    .append(" (").append(zone.getId()).append(")\n");
        }
        if (amount != null && rate.bid() != null) {
            BigDecimal converted = BigDecimal.valueOf(amount).multiply(rate.bid());
            sb.append(plain(BigDecimal.valueOf(amount))).append(' ').append(rate.from()).append(" = ")
                    .append(plain(converted.setScale(converted.abs().compareTo(BigDecimal.ONE) >= 0 ? 2 : 8, RoundingMode.HALF_UP)))
                    .append(' ').append(rate.to()).append('\n');
        }
        sb.append("Fonte: AwesomeAPI (economia.awesomeapi.com.br)");
        return sb.toString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }
}
