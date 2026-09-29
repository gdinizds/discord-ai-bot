package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ChannelMessage;

import java.time.Instant;
import java.util.List;

public interface ChannelLogPort {

    void append(ChannelMessage message);

    List<ChannelMessage> recent(String channelId, Instant since, int limit);
}
