package dev.gdinizds.discordaibot.application.port.in;

import dev.gdinizds.discordaibot.domain.model.LimitsCommand;

public interface HandleLimitsCommandUseCase {

    void handle(LimitsCommand command);
}
