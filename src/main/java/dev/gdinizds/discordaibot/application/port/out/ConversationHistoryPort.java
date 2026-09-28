package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;

import java.util.List;

public interface ConversationHistoryPort {

    List<ChatTurn> lastTurns(ConversationKey key, int limit);

    void append(List<ChatTurn> turns);
}

