package dev.gdinizds.discordaibot.application.service;

import dev.gdinizds.discordaibot.domain.model.InputAttachment;

public record LoadedAttachment(InputAttachment attachment, byte[] data, String problem) {

    public static LoadedAttachment ok(InputAttachment attachment, byte[] data) {
        return new LoadedAttachment(attachment, data, null);
    }

    public static LoadedAttachment ignored(InputAttachment attachment, String problem) {
        return new LoadedAttachment(attachment, null, problem);
    }

    public boolean isOk() {
        return problem == null;
    }
}

