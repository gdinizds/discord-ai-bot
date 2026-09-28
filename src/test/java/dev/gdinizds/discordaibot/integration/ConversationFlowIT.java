package dev.gdinizds.discordaibot.integration;

import dev.gdinizds.discordaibot.support.ScriptedChatModel;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.gdinizds.discordaibot.support.Fixtures.GUILD_ID;
import static dev.gdinizds.discordaibot.support.Fixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConversationFlowIT extends IntegrationTest {

    private static final String INTERACTIONS = "discord.events.interaction.command";
    private static final String COMMANDS = "discord.events.message.command";
    private static final String MESSAGES = "discord.events.message.created";
    private static final String RESPONSES = "discord.gateway.responses";

    private static KafkaConsumer<String, String> responseConsumer;
    private static final List<ConsumerRecord<String, String>> responses = new CopyOnWriteArrayList<>();

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ScriptedChatModel model;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void subscribeToResponses() {
        TestContainers.start();
        responseConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestContainers.REDPANDA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-responses-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        responseConsumer.subscribe(List.of(RESPONSES));
    }

    @AfterAll
    static void closeConsumer() {
        responseConsumer.close();
    }

    @BeforeEach
    void resetModel() {
        model.reset();
    }

    @Test
    void mentionGetsPlaceholderThenEdit() {
        model.thenAnswer("Amanhã faz sol.");
        String cid = publish(MESSAGES, null, "mention");

        List<JsonNode> out = awaitResponses(cid, 2);

        assertThat(out).extracting(n -> n.path("responseType").asString()).containsExactly("REPLY", "UPDATE_MESSAGE");
        assertThat(out.getFirst().path("content").asString()).isEqualTo("Pensando...");
        assertThat(out.getLast().path("content").asString()).isEqualTo("Amanhã faz sol.");
    }

    @Test
    void slashCommandIsASingleFinishedDeferredReply() {
        model.thenAnswer("Camberra.");
        String cid = publish(INTERACTIONS, "ia", "slash-ia");

        List<JsonNode> out = awaitResponses(cid, 1);

        assertThat(out.getFirst().path("responseType").asString()).isEqualTo("DEFERRED_REPLY");
        assertThat(out.getFirst().path("finished").asBoolean()).isTrue();
        assertThat(out.getFirst().path("interactionToken").asString()).isEqualTo("token-slash-1");
    }

    @Test
    void limitsCommandAnswersWithTheUsageReport() {
        String cid = publish(INTERACTIONS, "ia-limites", "ia-memoria-esquecer");

        List<JsonNode> out = awaitResponses(cid, 1);

        assertThat(out.getFirst().path("responseType").asString()).isEqualTo("DEFERRED_REPLY");
        assertThat(out.getFirst().path("content").asString()).contains("Seus limites de uso da IA", "Total da IA neste mês");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void longAnswerIsSplitWithFinishedOnlyOnTheLast() {
        model.thenAnswer(String.join("\n\n", "a".repeat(1500), "b".repeat(1500), "c".repeat(1500)));
        String cid = publish(INTERACTIONS, "ia", "slash-ia");

        List<JsonNode> out = awaitResponses(cid, 3);

        assertThat(out).extracting(n -> n.path("responseType").asString())
                .containsExactly("DEFERRED_REPLY", "REPLY", "REPLY");
        assertThat(out).extracting(n -> n.path("finished").asBoolean()).containsExactly(false, false, true);
    }

    @Test
    void redeliveredEventIsAnsweredOnce() {
        String cid = UUID.randomUUID().toString();
        String payload = withCorrelationId(event("slash-ia"), cid);
        send(INTERACTIONS, "ia", payload);
        send(INTERACTIONS, "ia", payload);

        awaitResponses(cid, 1);
        sleep(Duration.ofSeconds(3));

        assertThat(responsesFor(cid)).hasSize(1);
    }

    @Test
    void replyToAMessageWithoutHashProducesNothing() {
        String cid = UUID.randomUUID().toString();
        String payload = event("reply-to-bot").replace("A capital da Austrália é Camberra.", "Mensagem de outro bot " + cid);
        send(MESSAGES, null, withCorrelationId(payload, cid));

        sleep(Duration.ofSeconds(4));

        assertThat(responsesFor(cid)).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void replyToOurOwnAnswerIsAnswered() {
        model.thenAnswer("A capital da Austrália é Camberra.");
        awaitResponses(publish(INTERACTIONS, "ia", "slash-ia"), 1);

        model.thenAnswer("Sydney é a maior cidade, mas não a capital.");
        String cid = publish(MESSAGES, null, "reply-to-bot");

        List<JsonNode> out = awaitResponses(cid, 2);
        assertThat(out.getLast().path("content").asString()).isEqualTo("Sydney é a maior cidade, mas não a capital.");
    }

    @Test
    void pngFromGarageReachesTheModelAsImage() {
        String key = GUILD_ID + "/" + UUID.randomUUID() + "/print.png";
        try (S3Client s3 = TestContainers.s3()) {
            s3.putObject(b -> b.bucket(TestContainers.BUCKET).key(key),
                    RequestBody.fromBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}));
        }
        String url = TestContainers.s3Url() + "/" + TestContainers.BUCKET + "/" + key;
        String payload = event("dot-ia").replaceFirst("\"attachments\":\\[[^\\]]*\\]", "\"attachments\":[\"" + url + "\"]");
        model.thenAnswer("É um print.");
        String cid = UUID.randomUUID().toString();
        send(COMMANDS, "ia", withCorrelationId(payload, cid));

        awaitResponses(cid, 2);

        UserMessage current = (UserMessage) model.requests().getFirst().messages().getLast();
        assertThat(current.contents()).anySatisfy(c -> assertThat(c).isInstanceOf(ImageContent.class));
    }

    private String publish(String topic, String commandName, String fixture) {
        String cid = UUID.randomUUID().toString();
        send(topic, commandName, withCorrelationId(event(fixture), cid));
        return cid;
    }

    private void send(String topic, String commandName, String payload) {
        var record = new ProducerRecord<String, String>(topic, GUILD_ID, payload);
        if (commandName != null) record.headers().add("command-name", commandName.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).join();
    }

    private static String withCorrelationId(String payload, String cid) {
        return payload.replaceFirst("\"correlationId\":\"[^\"]+\"", "\"correlationId\":\"" + cid + "\"");
    }

    private List<JsonNode> awaitResponses(String cid, int count) {
        await().pollInSameThread().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            poll();
            return responsesFor(cid).size() >= count;
        });
        return responsesFor(cid).stream().map(r -> objectMapper.readTree(r.value())).toList();
    }

    private List<ConsumerRecord<String, String>> responsesFor(String cid) {
        poll();
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        for (var r : responses) if (cid.equals(r.key())) matching.add(r);
        return matching;
    }

    private static synchronized void poll() {
        responseConsumer.poll(Duration.ofMillis(100)).forEach(responses::add);
    }

    private static void sleep(Duration duration) {
        long end = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < end) poll();
    }
}

