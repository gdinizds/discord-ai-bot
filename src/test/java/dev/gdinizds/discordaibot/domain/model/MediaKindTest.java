package dev.gdinizds.discordaibot.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaKindTest {

    @Test
    void detectsPngFromMagicBytesRegardlessOfExtension() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(MediaKind.detectMimeType(pngBytes, "screenshot.jpg")).isEqualTo("image/png");
        assertThat(MediaKind.detectMimeType(pngBytes, "image.webp")).isEqualTo("image/png");
    }

    @Test
    void detectsJpegFromMagicBytesRegardlessOfExtension() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
        assertThat(MediaKind.detectMimeType(jpegBytes, "screenshot.png")).isEqualTo("image/jpeg");
    }

    @Test
    void detectsWebpFromMagicBytesRegardlessOfExtension() {
        byte[] webpBytes = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(MediaKind.detectMimeType(webpBytes, "screenshot.png")).isEqualTo("image/webp");
    }

    @Test
    void detectsGifFromMagicBytesRegardlessOfExtension() {
        byte[] gifBytes = new byte[]{'G', 'I', 'F', '8', '9', 'a'};
        assertThat(MediaKind.detectMimeType(gifBytes, "animation.png")).isEqualTo("image/gif");
    }

    @Test
    void detectsHeicFromMagicBytesRegardlessOfExtension() {
        byte[] heicBytes = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        assertThat(MediaKind.detectMimeType(heicBytes, "photo.jpg")).isEqualTo("image/heic");
    }

    @Test
    void detectsPdfFromMagicBytesRegardlessOfExtension() {
        byte[] pdfBytes = "%PDF-1.7".getBytes();
        assertThat(MediaKind.detectMimeType(pdfBytes, "document.bin")).isEqualTo("application/pdf");
    }

    @Test
    void fallsBackToExtensionWhenBytesDoNotMatch() {
        byte[] randomBytes = new byte[]{1, 2, 3};
        assertThat(MediaKind.detectMimeType(randomBytes, "file.png")).isEqualTo("image/png");
        assertThat(MediaKind.detectMimeType(randomBytes, "file.jpg")).isEqualTo("image/jpeg");
        assertThat(MediaKind.detectMimeType(randomBytes, "file.pdf")).isEqualTo("application/pdf");
        assertThat(MediaKind.detectMimeType(null, "file.webp")).isEqualTo("image/webp");
    }
}
