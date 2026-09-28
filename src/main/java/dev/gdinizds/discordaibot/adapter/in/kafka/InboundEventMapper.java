package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.domain.model.ConversationKey;
import dev.gdinizds.discordaibot.domain.model.ConversationRequest;
import dev.gdinizds.discordaibot.domain.model.InputAttachment;
import dev.gdinizds.discordaibot.domain.model.MediaKind;
import dev.gdinizds.discordaibot.domain.model.MemoryCommand;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;
import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.service.MentionParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public class InboundEventMapper {

    private final String botUserId;
    private final Clock clock;

    public InboundEventMapper(String botUserId, Clock clock) {
        this.botUserId = botUserId;
        this.clock = clock;
    }

    public ConversationRequest fromSlash(InboundEvent event) {
        String prompt = text(raw(event).path("args").path("pergunta"));
        return request(event, TriggerType.SLASH, prompt, null, List.of(),
                new ReplyTarget.Interaction(event.interactionToken()));
    }

    public ConversationRequest fromDotCommand(InboundEvent event) {
        JsonNode raw = raw(event);
        String prompt = text(raw.path("content"));
        if (prompt == null) {
            List<String> args = new ArrayList<>();
            for (JsonNode arg : raw.path("args")) args.add(arg.asString());
            prompt = String.join(" ", args);
        }
        return request(event, TriggerType.DOT, prompt, referencedContent(raw), attachments(event), channelTarget(event));
    }

    public ConversationRequest fromMessage(InboundEvent event, TriggerType trigger) {
        JsonNode raw = raw(event);
        String content = text(raw.path("content"));
        String prompt = trigger == TriggerType.MENTION
                ? MentionParser.stripMention(content, botUserId)
                : content;
        return request(event, trigger, prompt, referencedContent(raw), attachments(event), channelTarget(event));
    }

    public MemoryCommand toMemoryCommand(InboundEvent event) {
        JsonNode args = raw(event).path("args");
        return new MemoryCommand(event.correlationId(), event.guildId(), event.userId(),
                event.interactionToken(), text(args.path("acao")), text(args.path("id")));
    }

    private ConversationRequest request(InboundEvent event, TriggerType trigger, String prompt, String quoted,
                                        List<InputAttachment> attachments, ReplyTarget target) {
        var key = new ConversationKey(event.guildId(), event.channelId(), event.userId());
        String guildName = event.guild() == null ? null : event.guild().name();
        String username = event.user() == null ? null : event.user().username();
        return new ConversationRequest(event.correlationId(), trigger, key, guildName, username,
                prompt == null ? "" : prompt.strip(), quoted, attachments, target, clock.instant());
    }

    private static ReplyTarget channelTarget(InboundEvent event) {
        return new ReplyTarget.Channel(event.channelId(), event.messageId());
    }

    private static String referencedContent(JsonNode raw) {
        String content = text(raw.path("referencedMessage").path("content"));
        return content == null || content.isBlank() ? null : content;
    }

    static String referencedUserId(InboundEvent event) {
        return text(raw(event).path("referencedMessage").path("userId"));
    }

    static String content(InboundEvent event) {
        return text(raw(event).path("content"));
    }

    private static List<InputAttachment> attachments(InboundEvent event) {
        if (event.attachments() == null) return List.of();
        return event.attachments().stream()
                .filter(url -> url != null && !url.isBlank())
                .map(url -> {
                    String name = fileName(url);
                    return new InputAttachment(url, name, MediaKind.fromFileName(name));
                })
                .toList();
    }

    static String fileName(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        String last = path.substring(path.lastIndexOf('/') + 1);
        return URLDecoder.decode(last.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static JsonNode raw(InboundEvent event) {
        return event.rawPayload() == null ? MissingNode.getInstance() : event.rawPayload();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return node.isValueNode() ? node.asString() : null;
    }
}

