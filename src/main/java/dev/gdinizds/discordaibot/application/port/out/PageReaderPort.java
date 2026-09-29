package dev.gdinizds.discordaibot.application.port.out;

import dev.gdinizds.discordaibot.domain.model.WebPage;

public interface PageReaderPort {

    WebPage read(String url);

    class PageRejectedException extends RuntimeException {
        public PageRejectedException(String reason) {
            super(reason);
        }
    }
}
