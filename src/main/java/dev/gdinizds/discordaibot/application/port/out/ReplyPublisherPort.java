package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;

import java.util.List;

public interface ReplyPublisherPort {

    void placeholder(ReplyTarget target, String correlationId);

    void publish(ReplyTarget target, String correlationId, List<String> chunks, List<OutboundImage> images);

    default void publish(ReplyTarget target, String correlationId, List<String> chunks) {
        publish(target, correlationId, chunks, List.of());
    }

    void publishDirect(ReplyTarget target, String correlationId, String content);

    void publishEphemeral(String interactionToken, String correlationId, List<String> chunks);
}
