package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import dev.gdinizds.discordaibot.domain.model.MediaKind;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static dev.gdinizds.discordaibot.support.Fixtures.BOT_USER_ID;
import static dev.gdinizds.discordaibot.support.Fixtures.CHANNEL_ID;
import static dev.gdinizds.discordaibot.support.Fixtures.GUILD_ID;
import static dev.gdinizds.discordaibot.support.Fixtures.USER_ID;
import static dev.gdinizds.discordaibot.support.Fixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class InboundEventMapperTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);
    private final InboundEventMapper mapper = new InboundEventMapper(BOT_USER_ID, clock);
    private final TriggerResolver resolver = new TriggerResolver(BOT_USER_ID);

    @Test
    void slashCommandUsesPerguntaAndInteractionTarget() {
        ConversationRequest request = mapper.fromSlash(parse("slash-ia"));

        assertThat(request.trigger()).isEqualTo(TriggerType.SLASH);
        assertThat(request.prompt()).isEqualTo("Qual a capital da Austrália?");
        assertThat(request.target()).isEqualTo(new ReplyTarget.Interaction("token-slash-1"));
        assertThat(request.attachments()).isEmpty();
        assertThat(request.key().guildId()).isEqualTo(GUILD_ID);
        assertThat(request.key().channelId()).isEqualTo(CHANNEL_ID);
        assertThat(request.key().userId()).isEqualTo(USER_ID);
        assertThat(request.guildName()).isEqualTo("HotBCT");
        assertThat(request.username()).isEqualTo("guilherme");
        assertThat(request.receivedAt()).isEqualTo(clock.instant());
    }

    @Test
    void dotCommandUsesContentPreservingLineBreaks() {
        ConversationRequest request = mapper.fromDotCommand(parse("dot-ia"));

        assertThat(request.trigger()).isEqualTo(TriggerType.DOT);
        assertThat(request.prompt()).isEqualTo("explique\n```sql\nselect 1;\n```");
        assertThat(request.target()).isEqualTo(new ReplyTarget.Channel(CHANNEL_ID, "600000000000000002"));
        assertThat(request.attachments()).singleElement().satisfies(att -> {
            assertThat(att.fileName()).isEqualTo("print.png");
            assertThat(att.kind()).isEqualTo(MediaKind.IMAGE);
            assertThat(att.objectKey()).endsWith("/discord-gateway-attachments/" + GUILD_ID + "/600000000000000002/print.png");
        });
    }

    @Test
    void dotCommandFromOldGatewayFallsBackToArgs() {
        assertThat(mapper.fromDotCommand(parse("dot-ia-legacy")).prompt()).isEqualTo("o que é java?");
    }

    @Test
    void mentionTokenIsRemoved() {
        var event = parse("mention");
        assertThat(resolver.resolve(event)).contains(TriggerType.MENTION);

        ConversationRequest request = mapper.fromMessage(event, TriggerType.MENTION);

        assertThat(request.prompt()).isEqualTo("qual a previsão\npara amanhã?");
        assertThat(request.target()).isEqualTo(new ReplyTarget.Channel(CHANNEL_ID, "600000000000000004"));
    }

    @Test
    void mentionWithoutTextBecomesAnEmptyRequestForHelp() {
        var event = parse("mention-only");
        assertThat(resolver.resolve(event)).contains(TriggerType.MENTION);
        assertThat(mapper.fromMessage(event, TriggerType.MENTION).isEmpty()).isTrue();
    }

    @Test
    void replyToBotKeepsReferencedContentAsQuote() {
        var event = parse("reply-to-bot");
        assertThat(resolver.resolve(event)).contains(TriggerType.REPLY);

        ConversationRequest request = mapper.fromMessage(event, TriggerType.REPLY);

        assertThat(request.prompt()).isEqualTo("e em Sydney?");
        assertThat(request.quotedContext()).isEqualTo("A capital da Austrália é Camberra.");
    }

    @Test
    void mentionAndReplyInTheSameMessageAreASingleMention() {
        assertThat(resolver.resolve(parse("mention-and-reply"))).contains(TriggerType.MENTION);
    }

    @Test
    void unrelatedMessageIsNotATrigger() {
        String payload = event("unrelated-message");
        assertThat(resolver.mayConcernBot(payload)).isFalse();
        assertThat(resolver.resolve(parse("unrelated-message"))).isEmpty();
    }

    @Test
    void limitsCommandCarriesTheOwnerAndToken() {
        var command = mapper.toLimitsCommand(parse("ia-memoria-esquecer"));

        assertThat(command.userId()).isEqualTo("700000000000000001");
        assertThat(command.guildId()).isEqualTo("900000000000000001");
        assertThat(command.interactionToken()).isEqualTo("token-memoria-1");
        assertThat(command.correlationId()).isEqualTo("0192a3b4-0000-7000-8000-000000000009");
    }

    @Test
    void memoryCommandReadsActionAndId() {
        var command = mapper.toMemoryCommand(parse("ia-memoria-esquecer"));
        assertThat(command.action()).isEqualTo("esquecer");
        assertThat(command.memoryId()).isEqualTo("42");
        assertThat(command.interactionToken()).isEqualTo("token-memoria-1");
    }

    @Test
    void fileNameIsDecodedFromTheUrl() {
        assertThat(InboundEventMapper.fileName("http://g/b/1/2/meu%20arquivo.pdf")).isEqualTo("meu arquivo.pdf");
    }

    private InboundEvent parse(String fixture) {
        return objectMapper.readValue(event(fixture), InboundEvent.class);
    }

    @Test
    void channelMessagesUseTheDiscordTimestampOfTheMessageId() {
        assertThat(InboundEventMapper.snowflakeInstant("1234567890123456789"))
                .isEqualTo(Instant.ofEpochMilli(1_714_414_322_167L));
        assertThat(InboundEventMapper.snowflakeInstant("não")).isNull();
        assertThat(InboundEventMapper.snowflakeInstant(null)).isNull();
    }
}
