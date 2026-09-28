package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.in.HandleMemoryCommandUseCase;
import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.gdinizds.discordaibot.application.port.out.ProcessedEventPort;
import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.domain.model.MemoryCommand;
import dev.gdinizds.discordaibot.domain.model.UserMemory;
import dev.gdinizds.discordaibot.domain.service.MessageSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class MemoryCommandService implements HandleMemoryCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryCommandService.class);

    private final ManageMemoryUseCase memories;
    private final ProcessedEventPort processedEvents;
    private final ReplyPublisherPort replyPublisher;
    private final MessageSplitter splitter;
    private final String fallbackText;

    public MemoryCommandService(ManageMemoryUseCase memories, ProcessedEventPort processedEvents,
                                ReplyPublisherPort replyPublisher, MessageSplitter splitter,
                                String fallbackText) {
        this.memories = memories;
        this.processedEvents = processedEvents;
        this.replyPublisher = replyPublisher;
        this.splitter = splitter;
        this.fallbackText = fallbackText;
    }

    @Override
    public void handle(MemoryCommand command) {
        if (!processedEvents.tryAcquire(command.correlationId())) return;

        String reply;
        try {
            reply = execute(command);
        } catch (RuntimeException e) {
            log.warn("Memory command failed: {}", e.toString());
            reply = fallbackText;
        }
        replyPublisher.publishEphemeral(command.interactionToken(), command.correlationId(), splitter.split(reply));
    }

    private String execute(MemoryCommand command) {
        String action = command.action() == null ? "" : command.action().strip().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "listar" -> formatList(memories.list(command.guildId(), command.userId()));
            case "esquecer" -> forget(command);
            case "esquecer-tudo" -> {
                int removed = memories.forgetAll(command.guildId(), command.userId());
                yield removed == 0
                        ? "Não havia memórias suas neste servidor."
                        : "Apaguei " + removed + (removed == 1 ? " memória." : " memórias.");
            }
            default -> "Ação inválida. Use `listar`, `esquecer` ou `esquecer-tudo`.";
        };
    }

    private String forget(MemoryCommand command) {
        long id;
        try {
            id = Long.parseLong(command.memoryId() == null ? "" : command.memoryId().strip());
        } catch (NumberFormatException e) {
            return "Informe o `id` da memória. Veja os números com `/ia-memoria acao:listar`.";
        }
        return memories.forget(command.guildId(), command.userId(), id)
                ? "Memória #" + id + " apagada."
                : "Não encontrei a memória #" + id + ".";
    }

    private static String formatList(List<UserMemory> list) {
        if (list.isEmpty()) return "Não tenho nenhuma memória sobre você neste servidor.";
        StringBuilder sb = new StringBuilder("**O que eu lembro sobre você neste servidor** (")
                .append(list.size()).append(")\n");
        for (UserMemory m : list) {
            sb.append("`#").append(m.id()).append("` ").append(label(m.category())).append(": ")
                    .append(m.content().replace('\n', ' ')).append('\n');
        }
        return sb.append("\nPara apagar uma: `/ia-memoria acao:esquecer id:<número>`.").toString();
    }

    private static String label(String category) {
        return switch (category) {
            case "PREFERENCE" -> "Preferência";
            case "SKILL" -> "Habilidade";
            case "CONTEXT" -> "Contexto";
            default -> "Fato";
        };
    }
}

