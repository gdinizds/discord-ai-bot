package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ImageHit;

import java.util.List;

public interface ImageSearchPort {

    List<ImageHit> searchImages(String query, int limit);
}
