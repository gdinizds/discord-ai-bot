package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.in.ManageMemoryUseCase;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;

public class SaveMemoryTool {

    static final String UNAVAILABLE = "Não consegui salvar a memória agora.";

    private final ManageMemoryUseCase memories;
    private final ToolSupport support;
    private final Duration timeout;
    private final String guildId;
    private final String userId;
    private final String correlationId;

    public SaveMemoryTool(ManageMemoryUseCase memories, ToolSupport support, Duration timeout,
                          String guildId, String userId, String correlationId) {
        this.memories = memories;
        this.support = support;
        this.timeout = timeout;
        this.guildId = guildId;
        this.userId = userId;
        this.correlationId = correlationId;
    }

    @Tool(name = "save_memory", value = """
            Guarda um fato durável sobre o usuário atual para conversas futuras. Use só quando o próprio \
            usuário disser algo explícito e duradouro sobre si: preferências, stack e ferramentas que usa, \
            projetos, como quer ser tratado. Não use para fatos passageiros, suposições, informações sobre \
            outras pessoas nem dados sensíveis (documentos, senhas, tokens, saúde, finanças).""")
    public String saveMemory(@P("o fato em uma frase curta, em terceira pessoa, até 500 caracteres") String content,
                             @P(value = "FACT, PREFERENCE, SKILL ou CONTEXT", required = false) String category) {
        return support.run("save_memory", timeout, UNAVAILABLE, () ->
                switch (memories.save(guildId, userId, content, category, correlationId)) {
                    case INSERTED -> "Memória salva.";
                    case UPDATED -> "Memória atualizada; substituiu um fato parecido.";
                    case REJECTED_SENSITIVE -> "Não salvei: o conteúdo parece ter dados sensíveis.";
                    case REJECTED_INVALID -> "Não salvei: conteúdo vazio, acima de 500 caracteres "
                            + "ou categoria inválida (use FACT, PREFERENCE, SKILL ou CONTEXT).";
                    case FAILED -> UNAVAILABLE;
                });
    }
}

