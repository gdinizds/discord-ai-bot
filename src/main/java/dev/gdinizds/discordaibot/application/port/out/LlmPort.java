package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;

public interface LlmPort {

    AiAnswer answer(LlmPrompt prompt);
}

