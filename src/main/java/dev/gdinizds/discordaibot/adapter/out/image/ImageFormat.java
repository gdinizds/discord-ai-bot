package dev.gdinizds.discordaibot.adapter.out.image;

import java.util.Optional;

public enum ImageFormat {

    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String mimeType;

    ImageFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public static Optional<ImageFormat> sniff(byte[] data) {
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(PNG);
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) return Optional.of(JPEG);
        if (startsWith(data, 0, 'G', 'I', 'F', '8')) return Optional.of(GIF);
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) return Optional.of(WEBP);
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xff) != expected[i]) return false;
        }
        return true;
    }
}
