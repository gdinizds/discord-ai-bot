package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ArticleSummary;

import java.util.Optional;

public interface EncyclopediaPort {

    Optional<ArticleSummary> summary(String query, String lang);
}

