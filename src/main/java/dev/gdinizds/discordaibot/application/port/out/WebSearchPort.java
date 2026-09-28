package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.SearchHit;

import java.util.List;

public interface WebSearchPort {

    List<SearchHit> search(String query, int limit);
}

