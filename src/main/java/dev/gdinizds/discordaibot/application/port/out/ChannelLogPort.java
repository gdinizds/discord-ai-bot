package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.ChannelMessage;

import java.time.Instant;
import java.util.List;

public interface ChannelLogPort {

    void append(ChannelMessage message);

    default List<ChannelMessage> search(ChannelQuery query) {
        return recent(query.channelId(), query.since(), query.limit());
    }

    default List<ChannelMessage> recent(String channelId, Instant since, int limit) {
        return search(new ChannelQuery(null, channelId, false, List.of(), null, since, limit));
    }

    record ChannelQuery(
            String guildId,
            String channelId,
            boolean serverWide,
            List<String> users,
            String query,
            Instant since,
            int limit
    ) {
        public ChannelQuery {
            users = users == null ? List.of() : List.copyOf(users);
        }
    }
}
