package dev.gdinizds.discordaibot.adapter.in.kafka;

import dev.gdinizds.discordaibot.domain.model.TriggerType;
import dev.gdinizds.discordaibot.domain.service.MentionParser;

import java.util.Optional;

public class TriggerResolver {

    private final String botUserId;

    public TriggerResolver(String botUserId) {
        this.botUserId = botUserId;
    }

    public Optional<TriggerType> resolve(InboundEvent event) {
        if (botUserId.equals(event.userId())) return Optional.empty();
        if (MentionParser.mentions(InboundEventMapper.content(event), botUserId)) {
            return Optional.of(TriggerType.MENTION);
        }
        if (botUserId.equals(InboundEventMapper.referencedUserId(event))) {
            return Optional.of(TriggerType.REPLY);
        }
        return Optional.empty();
    }

    public boolean mayConcernBot(String payload) {
        return payload != null && payload.contains(botUserId);
    }
}

