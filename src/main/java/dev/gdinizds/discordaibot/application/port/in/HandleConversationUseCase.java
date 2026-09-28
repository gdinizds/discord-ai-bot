package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.ConversationRequest;

public interface HandleConversationUseCase {

    void handle(ConversationRequest request);

    void rejectBusy(ConversationRequest request);
}

