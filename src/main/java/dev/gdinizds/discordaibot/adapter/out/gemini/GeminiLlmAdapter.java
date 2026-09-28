package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.application.port.out.LlmPort;
import dev.gdinizds.discordaibot.domain.model.AiAnswer;
import dev.gdinizds.discordaibot.domain.model.BinaryPart;
import dev.gdinizds.discordaibot.domain.model.ChatTurn;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.Result;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class GeminiLlmAdapter implements LlmPort {

    private final AssistantFactory assistants;

    public GeminiLlmAdapter(AssistantFactory assistants) {
        this.assistants = assistants;
    }

    @Override
    public AiAnswer answer(LlmPrompt prompt) {
        var memory = MessageWindowChatMemory.withMaxMessages(prompt.history().size() + 64);
        memory.add(SystemMessage.from(prompt.systemPrompt()));
        for (ChatTurn turn : prompt.history()) {
            memory.add(switch (turn.role()) {
                case USER -> UserMessage.from(turn.content());
                case ASSISTANT -> AiMessage.from(turn.content());
            });
        }

        Result<String> result = assistants.create(prompt.key(), prompt.correlationId(), memory)
                .chat(currentContents(prompt));

        List<String> toolsUsed = result.toolExecutions() == null ? List.of() : result.toolExecutions().stream()
                .map(execution -> execution.request().name())
                .distinct()
                .toList();
        return new AiAnswer(Objects.requireNonNullElse(result.content(), ""), toolsUsed, usage(result), false);
    }

    private static List<Content> currentContents(LlmPrompt prompt) {
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(prompt.userText()));
        for (BinaryPart part : prompt.binaries()) {
            String base64 = Base64.getEncoder().encodeToString(part.data());
            contents.add(switch (part.kind()) {
                case PDF -> PdfFileContent.from(base64, part.mimeType());
                default -> ImageContent.from(base64, part.mimeType());
            });
        }
        return contents;
    }

    private static TokenUsage usage(Result<String> result) {
        var usage = result.tokenUsage();
        if (usage == null) return TokenUsage.NONE;
        return new TokenUsage(
                Objects.requireNonNullElse(usage.inputTokenCount(), 0),
                Objects.requireNonNullElse(usage.outputTokenCount(), 0));
    }
}

