package dev.gdinizds.discordaibot.adapter.out.kafka;

import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.config.AiBotProperties;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class GatewayResponsePublisher implements ReplyPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayResponsePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String placeholderText;

    public GatewayResponsePublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                    AiBotProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = properties.topics().outboundResponses();
        this.placeholderText = properties.reply().placeholderText();
    }

    @Override
    public void placeholder(ReplyTarget target, String correlationId) {
        if (target instanceof ReplyTarget.Channel(String channelId, String originMessageId)) {
            send(List.of(GatewayResponse.channelReply(channelId, originMessageId, correlationId, placeholderText)));
        }
    }

    @Override
    public void publish(ReplyTarget target, String correlationId, List<String> chunks, List<OutboundImage> images) {
        List<GatewayResponse> responses = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            boolean last = i == chunks.size() - 1;
            responses.add(switch (target) {
                case ReplyTarget.Interaction(String token) -> i == 0
                        ? GatewayResponse.deferredReply(token, correlationId, chunk, last)
                        : GatewayResponse.hookReply(token, correlationId, chunk, last);
                case ReplyTarget.Channel(String channelId, String originMessageId) -> i == 0
                        ? GatewayResponse.updateMessage(channelId, originMessageId, correlationId, chunk)
                        : GatewayResponse.channelReply(channelId, originMessageId, correlationId, chunk);
            });
        }
        if (!images.isEmpty() && !responses.isEmpty()) {
            List<GatewayResponse.Attachment> files = images.stream()
                    .map(image -> new GatewayResponse.Attachment(image.url(), image.fileName(), image.description()))
                    .toList();
            responses.set(responses.size() - 1, responses.getLast().withAttachments(files));
        }
        send(responses);
    }

    @Override
    public void publishDirect(ReplyTarget target, String correlationId, String content) {
        send(List.of(switch (target) {
            case ReplyTarget.Interaction(String token) -> GatewayResponse.deferredReply(token, correlationId, content, true);
            case ReplyTarget.Channel(String channelId, String originMessageId) ->
                    GatewayResponse.channelReply(channelId, originMessageId, correlationId, content);
        }));
    }

    @Override
    public void publishEphemeral(String interactionToken, String correlationId, List<String> chunks) {
        List<GatewayResponse> responses = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            boolean last = i == chunks.size() - 1;
            responses.add(i == 0
                    ? GatewayResponse.deferredReply(interactionToken, correlationId, chunks.get(i), last)
                    : GatewayResponse.ephemeralReply(interactionToken, correlationId, chunks.get(i), last));
        }
        send(responses);
    }

    private void send(List<GatewayResponse> responses) {
        List<CompletableFuture<?>> futures = new ArrayList<>(responses.size());
        for (GatewayResponse response : responses) {
            String json = objectMapper.writeValueAsString(response);
            futures.add(kafkaTemplate.send(topic, response.correlationId(), json));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing responses", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish responses to " + topic, e);
        }
        log.debug("Published {} response(s) of types {}", responses.size(),
                responses.stream().map(GatewayResponse::responseType).toList());
    }
}

