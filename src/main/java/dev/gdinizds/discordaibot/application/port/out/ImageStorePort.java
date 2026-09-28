package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;

public interface ImageStorePort {

    OutboundImage store(String guildId, String correlationId, int index, ImageHit hit);
}
