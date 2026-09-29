package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.in.ManageRemindersUseCase;
import dev.gdinizds.discordaibot.domain.model.Reminder;
import dev.gdinizds.discordaibot.domain.model.ReminderResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ReminderTool {

    static final String UNAVAILABLE = "Lembretes indisponíveis no momento.";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ManageRemindersUseCase reminders;
    private final ToolSupport support;
    private final Duration timeout;
    private final String guildId;
    private final String channelId;
    private final String userId;
    private final String correlationId;
    private final ZoneId zone;

    public ReminderTool(ManageRemindersUseCase reminders, ToolSupport support, Duration timeout,
                        String guildId, String channelId, String userId, String correlationId, ZoneId zone) {
        this.reminders = reminders;
        this.support = support;
        this.timeout = timeout;
        this.guildId = guildId;
        this.channelId = channelId;
        this.userId = userId;
        this.correlationId = correlationId;
        this.zone = zone;
    }

    @Tool(name = "reminder", value = """
            Cria, lista ou cancela lembretes do próprio usuário. Na hora marcada o bot escreve neste canal \
            marcando o usuário. Para criar, use tempo relativo (30m, 2h, 1d) ou calcule a data e hora local a \
            partir de "Agora" no contexto. Confirme ao usuário o horário que a ferramenta devolver.""")
    public String reminder(@P("ação: criar, listar ou cancelar") String action,
                           @P(value = "quando, para criar: 30m, 2h, 1d ou data e hora local como 2026-10-01T09:00",
                                   required = false) String when,
                           @P(value = "texto do lembrete, para criar", required = false) String text,
                           @P(value = "número do lembrete, para cancelar", required = false) Long id) {
        if (guildId == null || channelId == null || userId == null) {
            return "Lembretes só funcionam em canais de servidor.";
        }
        String verb = action == null ? "" : action.strip().toLowerCase(Locale.ROOT);
        return support.run("reminder", timeout, UNAVAILABLE, () -> switch (verb) {
            case "criar", "create", "agendar" -> create(when, text);
            case "listar", "list" -> list();
            case "cancelar", "cancel", "apagar" -> cancel(id);
            default -> "Ação desconhecida: use criar, listar ou cancelar.";
        });
    }

    private String create(String when, String text) {
        ReminderResult result = reminders.schedule(guildId, channelId, userId, when, text, correlationId);
        if (result.status() == ReminderResult.Status.SCHEDULED) {
            return "Lembrete #" + result.id() + " criado para " + format(result.dueAt()) + " (" + zone.getId() + ").";
        }
        return "Lembrete não criado: " + result.detail() + ".";
    }

    private String list() {
        List<Reminder> pending = reminders.pending(guildId, userId);
        if (pending.isEmpty()) return "O usuário não tem lembretes pendentes.";
        StringBuilder sb = new StringBuilder("Lembretes pendentes:\n");
        for (Reminder r : pending) {
            sb.append('#').append(r.id()).append(" em ").append(format(r.dueAt())).append(": ")
                    .append(ToolSupport.cut(r.content(), 200)).append('\n');
        }
        return sb.toString();
    }

    private String cancel(Long id) {
        if (id == null) return "Informe o número do lembrete para cancelar.";
        return reminders.cancel(guildId, userId, id)
                ? "Lembrete #" + id + " cancelado."
                : "Lembrete #" + id + " não encontrado entre os pendentes do usuário.";
    }

    private String format(Instant instant) {
        return WHEN.format(instant.atZone(zone));
    }
}
