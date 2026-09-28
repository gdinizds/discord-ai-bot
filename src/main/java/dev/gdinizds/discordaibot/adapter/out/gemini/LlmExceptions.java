package dev.gdinizds.discordaibot.adapter.out.gemini;

import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;

final class LlmExceptions {

    private LlmExceptions() {}

    static RuntimeException translate(RuntimeException e) {
        return switch (e) {
            case ContentFilteredException cf -> new ContentBlockedException(cf);
            case RateLimitException rl -> new RateLimitedException(rl);
            case InvalidRequestException ir -> new InvalidLlmRequestException(ir);
            case InternalServerException is -> new ServiceUnavailableException(is);
            case HttpException http -> switch (http.statusCode()) {
                case 429 -> new RateLimitedException(http);
                case 500, 502, 503, 504 -> new ServiceUnavailableException(http);
                case 400 -> new InvalidLlmRequestException(http);
                default -> e;
            };
            default -> e;
        };
    }
}

