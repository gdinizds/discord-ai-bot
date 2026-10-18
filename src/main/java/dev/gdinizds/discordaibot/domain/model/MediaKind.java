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

    public static String detectMimeType(byte[] data, String fileName) {
        if (data != null && data.length >= 3) {
            // PNG: 89 50 4E 47
            if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                return "image/png";
            }
            // JPEG: FF D8 FF
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            // GIF: GIF87a or GIF89a
            if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                    && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
                return "image/gif";
            }
            // WebP: RIFF....WEBP
            if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
                return "image/webp";
            }
            // HEIC / HEIF: ....ftyp
            if (data.length >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
                return "image/heic";
            }
            // PDF: %PDF-
            if (data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-') {
                return "application/pdf";
            }
        }
        return mimeType(fileName);
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

