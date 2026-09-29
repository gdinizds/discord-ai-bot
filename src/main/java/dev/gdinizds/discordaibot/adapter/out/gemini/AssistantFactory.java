package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.gdinizds.discordaibot.adapter.out.gemini.tools.CalculatorTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ChannelContextTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ExchangeRateTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ImageSearchTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ReadUrlTool;
import dev.gdinizds.discordaibot.adapter.out.gemini.tools.ReminderTool;
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

import java.util.ArrayList;
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
    private final AssistantTools extras;

    public AssistantFactory(ChatModel chatModel, WebSearchPort webSearch, WeatherPort weather,
                            EncyclopediaPort encyclopedia, ManageMemoryUseCase memories,
                            ImageSearchPort imageSearch, ImageStorePort imageStore,
                            ToolSupport toolSupport, AiBotProperties properties) {
        this(chatModel, webSearch, weather, encyclopedia, memories, imageSearch, imageStore, toolSupport, properties,
                AssistantTools.none());
    }

    public AssistantFactory(ChatModel chatModel, WebSearchPort webSearch, WeatherPort weather,
                            EncyclopediaPort encyclopedia, ManageMemoryUseCase memories,
                            ImageSearchPort imageSearch, ImageStorePort imageStore,
                            ToolSupport toolSupport, AiBotProperties properties, AssistantTools extras) {
        this.extras = extras;
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
        this.extras = base.extras;
    }

    public AssistantFactory withChatModel(ChatModel other) {
        return new AssistantFactory(this, other);
    }

    public Session create(ConversationKey key, String correlationId, ChatMemory memory) {
        var imageTool = new ImageSearchTool(imageSearch, imageStore, toolSupport, images.searchTimeout(),
                key.guildId(), correlationId, images.maxPerAnswer());
        List<Object> sessionTools = new ArrayList<>(List.of(
                new WebSearchTool(webSearch, toolSupport, tools.searxngTimeout()),
                new WeatherTool(weather, toolSupport, tools.openMeteoTimeout()),
                new WikipediaTool(encyclopedia, toolSupport, tools.wikipediaTimeout()),
                new SaveMemoryTool(memories, toolSupport, tools.saveMemoryTimeout(),
                        key.guildId(), key.userId(), correlationId),
                imageTool,
                new CalculatorTool(toolSupport, tools.calculatorTimeout())));
        if (extras.pages() != null) {
            sessionTools.add(new ReadUrlTool(extras.pages(), toolSupport, tools.readUrlTimeout(), tools.readUrlMaxChars()));
        }
        if (extras.channelLog() != null && key.channelId() != null) {
            sessionTools.add(new ChannelContextTool(extras.channelLog(), toolSupport, tools.channelContextTimeout(),
                    key.channelId(), extras.zone(), extras.clock(), tools.channelContextMaxChars()));
        }
        if (extras.exchangeRates() != null) {
            sessionTools.add(new ExchangeRateTool(extras.exchangeRates(), toolSupport, tools.exchangeRateTimeout(),
                    extras.zone()));
        }
        if (extras.reminders() != null) {
            sessionTools.add(new ReminderTool(extras.reminders(), toolSupport, tools.reminderTimeout(),
                    key.guildId(), key.channelId(), key.userId(), correlationId, extras.zone()));
        }
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(sessionTools)
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .build();
        return new Session(assistant, imageTool::attached);
    }
}
