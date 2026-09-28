package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.HandleLimitsCommandUseCase;
import dev.gdinizds.discordaibot.application.port.out.ProcessedEventPort;
import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.domain.model.LimitsCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class LimitsCommandService implements HandleLimitsCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(LimitsCommandService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final UsageService usage;
    private final ProcessedEventPort processedEvents;
    private final ReplyPublisherPort replyPublisher;
    private final String fallbackText;

    public LimitsCommandService(UsageService usage, ProcessedEventPort processedEvents,
                                ReplyPublisherPort replyPublisher, String fallbackText) {
        this.usage = usage;
        this.processedEvents = processedEvents;
        this.replyPublisher = replyPublisher;
        this.fallbackText = fallbackText;
    }

    @Override
    public void handle(LimitsCommand command) {
        if (!processedEvents.tryAcquire(command.correlationId())) return;
        String reply;
        try {
            reply = format(usage.report(command.userId()));
        } catch (RuntimeException e) {
            log.warn("Limits report failed: {}", e.toString());
            reply = fallbackText;
        }
        replyPublisher.publishEphemeral(command.interactionToken(), command.correlationId(), List.of(reply));
    }

    static String format(UsageService.Report report) {
        if (!report.enabled()) return "Os limites de uso da IA estão desligados.";
        StringBuilder sb = new StringBuilder("**Seus limites de uso da IA**\n");
        if (report.exempt()) {
            sb.append("Você não tem limite pessoal.\n");
        } else {
            sb.append("Hoje: ").append(usd(report.userDayMicroUsd())).append(" de ").append(usd(report.userDayLimitUsd()))
                    .append(" · ").append(report.userDayRequests()).append(" de ").append(report.userDayRequestLimit())
                    .append(" perguntas\n");
            sb.append("Este mês: ").append(usd(report.userMonthMicroUsd())).append(" de ")
                    .append(usd(report.userMonthLimitUsd())).append('\n');
            sb.append("Ritmo: até ").append(report.perMinute()).append(" perguntas por minuto\n");
        }
        sb.append("\n**Total da IA neste mês**\n")
                .append(usd(report.monthMicroUsd())).append(" de ").append(usd(report.monthLimitUsd()))
                .append(" (").append(percent(report.monthMicroUsd(), report.monthLimitUsd())).append(")\n");
        sb.append("\nO limite diário renova à meia-noite e o mensal no dia 1, horário de ")
                .append(report.zone()).append('.');
        return sb.toString();
    }

    private static String usd(long microUsd) {
        return String.format(PT_BR, "US$ %.2f", microUsd / 1_000_000.0);
    }

    private static String usd(double value) {
        return String.format(PT_BR, "US$ %.2f", value);
    }

    private static String percent(long microUsd, double limitUsd) {
        if (limitUsd <= 0) return "0%";
        return String.format(PT_BR, "%.0f%%", Math.min(100.0, microUsd / 10_000.0 / limitUsd));
    }
}
