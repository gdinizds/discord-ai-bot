package dev.gdinizds.discordaibot.domain.model;

public record BinaryPart(String fileName, MediaKind kind, String mimeType, byte[] data) {}

