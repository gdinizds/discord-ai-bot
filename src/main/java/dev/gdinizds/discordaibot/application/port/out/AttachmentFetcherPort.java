package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.InputAttachment;

public interface AttachmentFetcherPort {

    byte[] fetch(InputAttachment attachment);
}

