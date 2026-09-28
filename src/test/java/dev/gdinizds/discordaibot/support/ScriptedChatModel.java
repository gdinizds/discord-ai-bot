package dev.gdinizds.discordaibot.support;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class ScriptedChatModel implements ChatModel {

    public static final String DEFAULT_ANSWER = "Resposta roteirizada.";

    private final Queue<Function<ChatRequest, ChatResponse>> script = new ConcurrentLinkedQueue<>();
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

    public ScriptedChatModel thenAnswer(String text) {
        script.add(request -> response(AiMessage.from(text)));
        return this;
    }

    public ScriptedChatModel thenCallTool(String name, String argumentsJson) {
        script.add(request -> response(AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + requests.size())
                .name(name)
                .arguments(argumentsJson)
                .build())));
        return this;
    }

    public ScriptedChatModel thenThrow(RuntimeException exception) {
        script.add(request -> {
            throw exception;
        });
        return this;
    }

    public ScriptedChatModel then(Function<ChatRequest, ChatResponse> step) {
        script.add(step);
        return this;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        var step = script.poll();
        return step == null ? response(AiMessage.from(DEFAULT_ANSWER)) : step.apply(request);
    }

    public List<ChatRequest> requests() {
        return requests;
    }

    public void reset() {
        script.clear();
        requests.clear();
    }

    private static ChatResponse response(AiMessage message) {
        return ChatResponse.builder()
                .aiMessage(message)
                .tokenUsage(new TokenUsage(100, 20))
                .build();
    }
}

