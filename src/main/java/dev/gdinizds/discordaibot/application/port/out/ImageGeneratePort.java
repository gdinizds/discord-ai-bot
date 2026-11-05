package dev.gdinizds.discordaibot.application.port.out;

public interface ImageGeneratePort {

    record GeneratedImage(byte[] data, String mimeType) {}

    GeneratedImage generate(String prompt);
}
