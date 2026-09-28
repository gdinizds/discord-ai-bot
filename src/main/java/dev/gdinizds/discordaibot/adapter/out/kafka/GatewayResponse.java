package dev.gdinizds.discordaibot.adapter.out.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayResponse(
        String responseType,
        String interactionToken,
        String messageId,
        String channelId,
        String correlationId,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Object> embeds,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Object> attachments,
        Boolean finished) {

    public static GatewayResponse deferredReply(String interactionToken, String correlationId,
                                                String content, boolean finished) {
        return new GatewayResponse("DEFERRED_REPLY", interactionToken, null, null, correlationId,
                content, null, null, finished);
    }

    public static GatewayResponse hookReply(String interactionToken, String correlationId,
                                            String content, boolean finished) {
        return new GatewayResponse("REPLY", interactionToken, null, null, correlationId,
                content, null, null, finished);
    }

    public static GatewayResponse ephemeralReply(String interactionToken, String correlationId,
                                                 String content, boolean finished) {
        return new GatewayResponse("EPHEMERAL_REPLY", interactionToken, null, null, correlationId,
                content, null, null, finished);
    }

    public static GatewayResponse channelReply(String channelId, String originMessageId, String correlationId,
                                               String content) {
        return new GatewayResponse("REPLY", null, originMessageId, channelId, correlationId,
                content, null, null, null);
    }

    public static GatewayResponse updateMessage(String channelId, String originMessageId, String correlationId,
                                                String content) {
        return new GatewayResponse("UPDATE_MESSAGE", null, originMessageId, channelId, correlationId,
                content, null, null, null);
    }
}

