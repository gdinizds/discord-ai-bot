package dev.gdinizds.discordaibot.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageIntegrityValidatorTest {

    @Test
    void rejectsNullOrTooShortData() {
        assertThat(ImageIntegrityValidator.isComplete(null)).isFalse();
        assertThat(ImageIntegrityValidator.isComplete(new byte[0])).isFalse();
        assertThat(ImageIntegrityValidator.isComplete(new byte[7])).isFalse();
    }

    @Test
    void acceptsCompletePng() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] iendChunk = new byte[]{0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        byte[] fullPng = new byte[pngHeader.length + 20 + iendChunk.length];
        System.arraycopy(pngHeader, 0, fullPng, 0, pngHeader.length);
        System.arraycopy(iendChunk, 0, fullPng, fullPng.length - iendChunk.length, iendChunk.length);

        assertThat(ImageIntegrityValidator.isComplete(fullPng)).isTrue();
    }

    @Test
    void rejectsTruncatedPngWithoutIend() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] truncatedPng = new byte[pngHeader.length + 50];
        System.arraycopy(pngHeader, 0, truncatedPng, 0, pngHeader.length);

        assertThat(ImageIntegrityValidator.isComplete(truncatedPng)).isFalse();
    }

    @Test
    void acceptsCompleteJpeg() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, (byte) 0xFF, (byte) 0xD9};
        assertThat(ImageIntegrityValidator.isComplete(jpeg)).isTrue();
    }

    @Test
    void rejectsTruncatedJpegWithoutEoi() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        assertThat(ImageIntegrityValidator.isComplete(jpeg)).isFalse();
    }

    @Test
    void acceptsCompleteGif() {
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00, 0x3B};
        assertThat(ImageIntegrityValidator.isComplete(gif)).isTrue();
    }

    @Test
    void rejectsTruncatedGifWithoutTrailer() {
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00};
        assertThat(ImageIntegrityValidator.isComplete(gif)).isFalse();
    }
}
