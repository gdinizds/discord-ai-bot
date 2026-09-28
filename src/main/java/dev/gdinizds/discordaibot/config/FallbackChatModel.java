package dev.gdinizds.discordaibot.config;

import dev.langchain4j.model.chat.ChatModel;

public record FallbackChatModel(String name, ChatModel model) {}
