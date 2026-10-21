package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import dev.gdinizds.discordaibot.domain.model.LlmPrompt;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        ConversationSettings settings = new ConversationSettings(
                10,
                4000,
                5,
                0.8,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                5,
                10_000_000,
                100_000,
                ZoneId.of("America/Sao_Paulo"),
                "fallback",
                "busy",
                "help"
        );
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneId.of("UTC"));
        assembler = new ContextAssembler(settings, fixedClock);
    }

    @Test
    @DisplayName("Deve conter a diretriz estrita sobre religião no system prompt")
    void shouldIncludeReligionPolicyInSystemPrompt() {
        ConversationRequest request = new ConversationRequest(
                "corr-1",
                TriggerType.SLASH,
                new ConversationKey("guild-1", "chan-1", "user-1"),
                "MyGuild",
                "TestUser",
                "Qual a religião verdadeira?",
                null,
                List.of(),
                new ReplyTarget.Channel("chan-1", "resp-1"),
                Instant.now(),
                false
        );

        LlmPrompt prompt = assembler.assemble(request, List.of(), List.of(), List.of());

        assertThat(prompt.systemPrompt())
                .contains("Diretriz sobre Religião (Obrigatória e Inegociável):")
                .contains("NUNCA se envolva, debata ou responda sobre religião")
                .contains("NENHUM tipo de desrespeito")
                .contains("Sempre NEGUE e recuse prontamente qualquer pedido que envolva esses temas");
    }

    @Test
    @DisplayName("Deve incluir instruções de TTS somente quando speak for true")
    void shouldIncludeTtsInstructionsOnlyWhenSpeakIsTrue() {
        ConversationRequest withoutSpeak = new ConversationRequest(
                "corr-1",
                TriggerType.SLASH,
                new ConversationKey("guild-1", "chan-1", "user-1"),
                "MyGuild",
                "TestUser",
                "Olá",
                null,
                List.of(),
                new ReplyTarget.Channel("chan-1", "resp-1"),
                Instant.now(),
                false
        );
        ConversationRequest withSpeak = new ConversationRequest(
                "corr-2",
                TriggerType.SLASH,
                new ConversationKey("guild-1", "chan-1", "user-1"),
                "MyGuild",
                "TestUser",
                "Olá",
                null,
                List.of(),
                new ReplyTarget.Channel("chan-1", "resp-2"),
                Instant.now(),
                true
        );

        LlmPrompt promptWithoutSpeak = assembler.assemble(withoutSpeak, List.of(), List.of(), List.of());
        LlmPrompt promptWithSpeak = assembler.assemble(withSpeak, List.of(), List.of(), List.of());

        assertThat(promptWithoutSpeak.systemPrompt()).doesNotContain("Narração em Áudio (Text-to-Speech");
        assertThat(promptWithSpeak.systemPrompt())
                .contains("Narração em Áudio (Text-to-Speech - Fish Audio S2.1):")
                .contains("NUNCA ria ou escreva risadas com \"kkk\"");
    }

    @Test
    @DisplayName("Deve remover marcadores de emoção e manter links markdown e código")
    void shouldStripTtsTagsCorrectly() {
        assertThat(ContextAssembler.stripTtsTags("[happy] Olá! Que ótimo te ver por aqui."))
                .isEqualTo("Olá! Que ótimo te ver por aqui.");

        assertThat(ContextAssembler.stripTtsTags("[sad][whispering] Sinto muito por isso..."))
                .isEqualTo("Sinto muito por isso...");

        assertThat(ContextAssembler.stripTtsTags("Isso é [emphasis] muito importante."))
                .isEqualTo("Isso é muito importante.");

        assertThat(ContextAssembler.stripTtsTags("[curious] Você sabia disso? [surprised] É impressionante!"))
                .isEqualTo("Você sabia disso? É impressionante!");

        assertThat(ContextAssembler.stripTtsTags("[laughing] Ha ha ha! Conseguimos!"))
                .isEqualTo("Ha ha ha! Conseguimos!");

        assertThat(ContextAssembler.stripTtsTags("[break] Um momento..."))
                .isEqualTo("Um momento...");

        // Links Markdown NÃO devem ser removidos
        assertThat(ContextAssembler.stripTtsTags("[happy] Veja em [nosso site](https://example.com) agora."))
                .isEqualTo("Veja em [nosso site](https://example.com) agora.");

        // Parênteses legados S1 também são limpos
        assertThat(ContextAssembler.stripTtsTags("(happy) Bom dia!"))
                .isEqualTo("Bom dia!");

        // Array / índice de código não é removido
        assertThat(ContextAssembler.stripTtsTags("valor: list[0] e array[index]"))
                .isEqualTo("valor: list[0] e array[index]");
    }
}
