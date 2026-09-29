package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.ChannelMessage;

public interface RecordChannelMessageUseCase {

    void record(ChannelMessage message);
}
