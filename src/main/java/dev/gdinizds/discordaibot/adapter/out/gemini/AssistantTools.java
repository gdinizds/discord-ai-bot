package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.application.port.in.ManageRemindersUseCase;
import dev.gdinizds.discordaibot.application.port.out.ChannelLogPort;
import dev.gdinizds.discordaibot.application.port.out.ExchangeRatePort;
import dev.gdinizds.discordaibot.application.port.out.PageReaderPort;

import java.time.Clock;
import java.time.ZoneId;

public record AssistantTools(
        PageReaderPort pages,
        ChannelLogPort channelLog,
        ExchangeRatePort exchangeRates,
        ManageRemindersUseCase reminders,
        ZoneId zone,
        Clock clock) {

    public static AssistantTools none() {
        return new AssistantTools(null, null, null, null, ZoneId.of("America/Sao_Paulo"), Clock.systemUTC());
    }
}
