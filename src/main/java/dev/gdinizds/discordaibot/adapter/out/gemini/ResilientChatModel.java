package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.config.Resilience;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Set;

public class ResilientChatModel implements ChatModel {

    static final String INSTANCE = "gemini-chat";
    public static final String FALLBACK_INSTANCE = "gemini-chat-fallback";

    private final ChatModel delegate;
    private final Resilience resilience;
    private final String instance;

    public ResilientChatModel(ChatModel delegate, Resilience resilience) {
        this(delegate, resilience, INSTANCE);
    }

    public ResilientChatModel(ChatModel delegate, Resilience resilience, String instance) {
        this.delegate = delegate;
        this.resilience = resilience;
        this.instance = instance;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return resilience.call(instance, () -> {
            try {
                return delegate.chat(request);
            } catch (RuntimeException e) {
                throw LlmExceptions.translate(e);
            }
        });
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}

