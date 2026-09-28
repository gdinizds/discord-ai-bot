package dev.gdinizds.discordaibot.support;

import dev.gdinizds.discordaibot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordaibot.domain.model.ReplyTarget;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingReplyPublisher implements ReplyPublisherPort {

    public enum Kind { PLACEHOLDER, PUBLISH, DIRECT, EPHEMERAL }

    public record Call(Kind kind, ReplyTarget target, String correlationId, List<String> chunks) {}

    public final List<Call> calls = new CopyOnWriteArrayList<>();

    @Override
    public void placeholder(ReplyTarget target, String correlationId) {
        calls.add(new Call(Kind.PLACEHOLDER, target, correlationId, List.of()));
    }

    @Override
    public void publish(ReplyTarget target, String correlationId, List<String> chunks) {
        calls.add(new Call(Kind.PUBLISH, target, correlationId, chunks));
    }

    @Override
    public void publishDirect(ReplyTarget target, String correlationId, String content) {
        calls.add(new Call(Kind.DIRECT, target, correlationId, List.of(content)));
    }

    @Override
    public void publishEphemeral(String interactionToken, String correlationId, List<String> chunks) {
        calls.add(new Call(Kind.EPHEMERAL, new ReplyTarget.Interaction(interactionToken), correlationId, chunks));
    }

    public List<Kind> kinds() {
        return calls.stream().map(Call::kind).toList();
    }

    public Call last() {
        return calls.getLast();
    }
}

