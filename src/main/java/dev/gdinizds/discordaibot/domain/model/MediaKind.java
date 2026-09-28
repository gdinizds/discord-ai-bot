package dev.gdinizds.discordaibot.domain.model;

import java.util.Locale;
import java.util.Set;

public enum MediaKind {
    IMAGE, PDF, TEXT, UNSUPPORTED;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "json", "log", "csv", "java", "sql", "yaml", "yml", "xml");

    public static MediaKind fromFileName(String fileName) {
        String ext = extension(fileName);
        if (IMAGE_EXTENSIONS.contains(ext)) return IMAGE;
        if ("pdf".equals(ext)) return PDF;
        if (TEXT_EXTENSIONS.contains(ext)) return TEXT;
        return UNSUPPORTED;
    }

    public static String mimeType(String fileName) {
        return switch (extension(fileName)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            default -> "text/plain";
        };
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

