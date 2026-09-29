package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.application.port.out.ChannelLogPort;
import dev.gdinizds.discordaibot.domain.model.ChannelMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ChannelLogServiceTest {

    private static final Instant AT = Instant.parse("2026-09-29T12:00:00Z");

    private final List<ChannelMessage> stored = new ArrayList<>();
    private final ChannelLogPort port = new ChannelLogPort() {
        @Override public void append(ChannelMessage message) { stored.add(message); }
        @Override public List<ChannelMessage> recent(String channelId, Instant since, int limit) { return List.of(); }
    };

    @Test
    void storesTrimmedMessages() {
        new ChannelLogService(port, true, 1000).record(message("  oi pessoal  "));

        assertThat(stored).singleElement().extracting(ChannelMessage::content).isEqualTo("oi pessoal");
    }

    @Test
    void cutsLongMessagesWithoutSplittingSurrogatePairs() {
        new ChannelLogService(port, true, 10).record(message("123456789😀😀😀"));

        assertThat(stored.getFirst().content()).isEqualTo("123456789…");
    }

    @Test
    void skipsEmptyIncompleteOrDisabled() {
        new ChannelLogService(port, true, 1000).record(message("   "));
        new ChannelLogService(port, true, 1000).record(new ChannelMessage("1", null, "10", "3", "ana", "oi", AT));
        new ChannelLogService(port, false, 1000).record(message("oi"));

        assertThat(stored).isEmpty();
    }

    @Test
    void storageFailureNeverBreaksTheConsumer() {
        ChannelLogPort failing = new ChannelLogPort() {
            @Override public void append(ChannelMessage message) { throw new IllegalStateException("db down"); }
            @Override public List<ChannelMessage> recent(String channelId, Instant since, int limit) { return List.of(); }
        };

        assertThatCode(() -> new ChannelLogService(failing, true, 1000).record(message("oi"))).doesNotThrowAnyException();
    }

    private static ChannelMessage message(String content) {
        return new ChannelMessage("1", "2", "10", "3", "ana", content, AT);
    }
}
