package dev.gdinizds.discordaibot.adapter.out.kafka;

import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GatewayResponsePublisherTest {

    private static final String TOPIC = "discord.gateway.responses";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private GatewayResponsePublisher publisher;

    @BeforeEach
    void setUp() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var properties = new AiBotProperties(null,
                new AiBotProperties.Topics("a", "b", "c", TOPIC, "e"),
                null, null,
                new AiBotProperties.Reply(1900, 5, "Pensando...", Duration.ofMillis(1500)),
                null, null, null, null, null, null, null);
        publisher = new GatewayResponsePublisher(kafka, objectMapper, properties);
    }

    @Test
    void interactionAnswerIsDeferredReplyThenHookRepliesFinishedOnlyOnTheLast() {
        publisher.publish(new ReplyTarget.Interaction("tok"), "cid", List.of("um", "dois", "três"));

        List<JsonNode> sent = sent(3);
        assertThat(sent).extracting(n -> n.path("responseType").asString())
                .containsExactly("DEFERRED_REPLY", "REPLY", "REPLY");
        assertThat(sent).extracting(n -> n.path("finished").asBoolean()).containsExactly(false, false, true);
        assertThat(sent).allSatisfy(n -> {
            assertThat(n.path("interactionToken").asString()).isEqualTo("tok");
            assertThat(n.has("channelId")).isFalse();
        });
    }

    @Test
    void singleInteractionChunkIsFinished() {
        publisher.publish(new ReplyTarget.Interaction("tok"), "cid", List.of("só um"));

        assertThat(sent(1).getFirst().path("finished").asBoolean()).isTrue();
    }

    @Test
    void channelAnswerEditsThePlaceholderThenReplies() {
        var target = new ReplyTarget.Channel("chan", "origin");
        publisher.placeholder(target, "cid");
        publisher.publish(target, "cid", List.of("um", "dois"));

        List<JsonNode> sent = sent(3);
        assertThat(sent).extracting(n -> n.path("responseType").asString())
                .containsExactly("REPLY", "UPDATE_MESSAGE", "REPLY");
        assertThat(sent.getFirst().path("content").asString()).isEqualTo("Pensando...");
        assertThat(sent).allSatisfy(n -> {
            assertThat(n.path("channelId").asString()).isEqualTo("chan");
            assertThat(n.path("messageId").asString()).isEqualTo("origin");
            assertThat(n.has("finished")).isFalse();
            assertThat(n.has("interactionToken")).isFalse();
        });
    }

    @Test
    void imagesGoOnlyOnTheLastMessage() {
        var image = new OutboundImage("http://garage:3900/bucket/ai-bot/g/cid/imagem-1.png", "imagem-1.png", "Capivara");
        publisher.publish(new ReplyTarget.Interaction("tok"), "cid", List.of("um", "dois"), List.of(image));

        List<JsonNode> sent = sent(2);
        assertThat(sent.getFirst().has("attachments")).isFalse();
        JsonNode attachment = sent.getLast().path("attachments").get(0);
        assertThat(attachment.path("url").asString()).isEqualTo("http://garage:3900/bucket/ai-bot/g/cid/imagem-1.png");
        assertThat(attachment.path("name").asString()).isEqualTo("imagem-1.png");
        assertThat(attachment.path("description").asString()).isEqualTo("Capivara");
        assertThat(sent.getLast().path("finished").asBoolean()).isTrue();
    }

    @Test
    void singleChannelChunkCarriesTheImagesOnTheEdit() {
        var image = new OutboundImage("http://garage:3900/bucket/k.jpg", "imagem-1.jpg", null);
        publisher.publish(new ReplyTarget.Channel("chan", "origin"), "cid", List.of("aqui"), List.of(image));

        JsonNode only = sent(1).getFirst();
        assertThat(only.path("responseType").asString()).isEqualTo("UPDATE_MESSAGE");
        assertThat(only.path("attachments").get(0).has("description")).isFalse();
    }

    @Test
    void placeholderIsSkippedForInteractions() {
        publisher.placeholder(new ReplyTarget.Interaction("tok"), "cid");
        verify(kafka, times(0)).send(anyString(), anyString(), anyString());
    }

    @Test
    void ephemeralFollowupsUseEphemeralReply() {
        publisher.publishEphemeral("tok", "cid", List.of("um", "dois"));

        assertThat(sent(2)).extracting(n -> n.path("responseType").asString())
                .containsExactly("DEFERRED_REPLY", "EPHEMERAL_REPLY");
    }

    private List<JsonNode> sent(int count) {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(count)).send(eq(TOPIC), eq("cid"), json.capture());
        return json.getAllValues().stream().map(objectMapper::readTree).toList();
    }
}

