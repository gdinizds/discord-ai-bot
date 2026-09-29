package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.RecordChannelMessageUseCase;
import dev.gdinizds.discordaibot.application.port.out.ChannelLogPort;
import dev.gdinizds.discordaibot.domain.model.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelLogService implements RecordChannelMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChannelLogService.class);

    private final ChannelLogPort channelLog;
    private final boolean enabled;
    private final int maxContentChars;

    public ChannelLogService(ChannelLogPort channelLog, boolean enabled, int maxContentChars) {
        this.channelLog = channelLog;
        this.enabled = enabled;
        this.maxContentChars = maxContentChars;
    }

    @Override
    public void record(ChannelMessage message) {
        if (!enabled || message.channelId() == null || message.messageId() == null || message.userId() == null) return;
        String content = message.content() == null ? "" : message.content().strip();
        if (content.isEmpty()) return;
        if (content.length() > maxContentChars) {
            int end = Character.isHighSurrogate(content.charAt(maxContentChars - 1)) ? maxContentChars - 1 : maxContentChars;
            content = content.substring(0, end) + "…";
        }
        try {
            channelLog.append(new ChannelMessage(message.guildId(), message.channelId(), message.messageId(),
                    message.userId(), message.username(), content, message.createdAt()));
        } catch (RuntimeException e) {
            log.debug("Channel message not recorded: {}", e.toString());
        }
    }
}
