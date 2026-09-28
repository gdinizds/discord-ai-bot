package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.MemoryCommand;

public interface HandleMemoryCommandUseCase {

    void handle(MemoryCommand command);
}

