package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;

import java.util.ArrayList;
import java.util.List;

public interface ImageStorePort {

    record Stored(ImageHit hit, OutboundImage image) {}

    OutboundImage store(String guildId, String correlationId, int index, ImageHit hit);

    default List<Stored> storeFirst(String guildId, String correlationId, int firstIndex,
                                    List<ImageHit> candidates, int wanted) {
        List<Stored> stored = new ArrayList<>();
        for (ImageHit hit : candidates) {
            if (stored.size() >= wanted) break;
            try {
                stored.add(new Stored(hit, store(guildId, correlationId, firstIndex + stored.size(), hit)));
            } catch (RuntimeException ignored) {
            }
        }
        return stored;
    }
}
