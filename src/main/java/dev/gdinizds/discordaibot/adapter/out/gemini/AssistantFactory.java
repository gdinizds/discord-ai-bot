package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ImageSearchTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.SaveMemoryTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ToolSupport;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WeatherTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WebSearchTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.WikipediaTool;
import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.EncyclopediaPort;
import dev.gdinizds.discordaibot.application.port.out.ImageSearchPort;
import dev.gdinizds.discordaibot.application.port.out.ImageStorePort;
import dev.gdinizds.discordaibot.application.port.out.WeatherPort;
import dev.gdinizds.discordaibot.application.port.out.WebSearchPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;

import java.util.List;
import java.util.function.Supplier;

public class AssistantFactory {

    public interface Assistant {
        Result<String> chat(List<Content> contents);
    }

    public record Session(Assistant assistant, Supplier<List<OutboundImage>> images) {}

    private final ChatModel chatModel;
    private final WebSearchPort webSearch;
    private final WeatherPort weather;
    private final EncyclopediaPort encyclopedia;
    private final ManageMemoryUseCase memories;
    private final ImageSearchPort imageSearch;
    private final ImageStorePort imageStore;
    private final ToolSupport toolSupport;
    private final AiBotProperties.Tools tools;
    private final AiBotProperties.Images images;
    private final int maxSequentialToolInvocations;

    public AssistantFactory(ChatModel chatModel, WebSearchPort webSearch, WeatherPort weather,
                            EncyclopediaPort encyclopedia, ManageMemoryUseCase memories,
                            ImageSearchPort imageSearch, ImageStorePort imageStore,
                            ToolSupport toolSupport, AiBotProperties properties) {
        this.chatModel = chatModel;
        this.webSearch = webSearch;
        this.weather = weather;
        this.encyclopedia = encyclopedia;
        this.memories = memories;
        this.imageSearch = imageSearch;
        this.imageStore = imageStore;
        this.toolSupport = toolSupport;
        this.tools = properties.tools();
        this.images = properties.images();
        this.maxSequentialToolInvocations = properties.gemini().maxSequentialToolInvocations();
    }

    private AssistantFactory(AssistantFactory base, ChatModel chatModel) {
        this.chatModel = chatModel;
        this.webSearch = base.webSearch;
        this.weather = base.weather;
        this.encyclopedia = base.encyclopedia;
        this.memories = base.memories;
        this.imageSearch = base.imageSearch;
        this.imageStore = base.imageStore;
        this.toolSupport = base.toolSupport;
        this.tools = base.tools;
        this.images = base.images;
        this.maxSequentialToolInvocations = base.maxSequentialToolInvocations;
    }

    public AssistantFactory withChatModel(ChatModel other) {
        return new AssistantFactory(this, other);
    }

    public Session create(ConversationKey key, String correlationId, ChatMemory memory) {
        var imageTool = new ImageSearchTool(imageSearch, imageStore, toolSupport, images.searchTimeout(),
                key.guildId(), correlationId, images.maxPerAnswer());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(
                        new WebSearchTool(webSearch, toolSupport, tools.searxngTimeout()),
                        new WeatherTool(weather, toolSupport, tools.openMeteoTimeout()),
                        new WikipediaTool(encyclopedia, toolSupport, tools.wikipediaTimeout()),
                        new SaveMemoryTool(memories, toolSupport, tools.saveMemoryTimeout(),
                                key.guildId(), key.userId(), correlationId),
                        imageTool)
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .build();
        return new Session(assistant, imageTool::attached);
    }
}
