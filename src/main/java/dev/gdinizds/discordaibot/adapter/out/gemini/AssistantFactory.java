package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.adapter.out.gemini.tools.SaveMemoryTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ToolSupport;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WeatherTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WebSearchTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WikipediaTool;
import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.EncyclopediaPort;
import dev.gdinizds.discordaibot.application.port.out.WeatherPort;
import dev.gdinizds.discordaibot.application.port.out.WebSearchPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;

import java.util.List;

public class AssistantFactory {

    public interface Assistant {
        Result<String> chat(List<Content> contents);
    }

    private final ChatModel chatModel;
    private final WebSearchPort webSearch;
    private final WeatherPort weather;
    private final EncyclopediaPort encyclopedia;
    private final ManageMemoryUseCase memories;
    private final ToolSupport toolSupport;
    private final AiBotProperties.Tools tools;
    private final int maxSequentialToolInvocations;

    public AssistantFactory(ChatModel chatModel, WebSearchPort webSearch, WeatherPort weather,
                            EncyclopediaPort encyclopedia, ManageMemoryUseCase memories,
                            ToolSupport toolSupport, AiBotProperties properties) {
        this.chatModel = chatModel;
        this.webSearch = webSearch;
        this.weather = weather;
        this.encyclopedia = encyclopedia;
        this.memories = memories;
        this.toolSupport = toolSupport;
        this.tools = properties.tools();
        this.maxSequentialToolInvocations = properties.gemini().maxSequentialToolInvocations();
    }

    public Assistant create(ConversationKey key, String correlationId, ChatMemory memory) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(
                        new WebSearchTool(webSearch, toolSupport, tools.searxngTimeout()),
                        new WeatherTool(weather, toolSupport, tools.openMeteoTimeout()),
                        new WikipediaTool(encyclopedia, toolSupport, tools.wikipediaTimeout()),
                        new SaveMemoryTool(memories, toolSupport, tools.saveMemoryTimeout(),
                                key.guildId(), key.userId(), correlationId))
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .build();
    }
}

