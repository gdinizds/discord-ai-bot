package dev.gdinizds.discordaibot.application.service;

import java.nio.charset.StandardCharsets;

public final class ImageIntegrityValidator {

    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PNG_IEND = "IEND".getBytes(StandardCharsets.US_ASCII);

    private ImageIntegrityValidator() {}

    public static boolean isComplete(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }

        if (isPng(data)) {
            return isPngComplete(data);
        }

        if (isJpeg(data)) {
            return isJpegComplete(data);
        }

        if (isGif(data)) {
            return isGifComplete(data);
        }

        if (isWebP(data)) {
            return isWebPComplete(data);
        }

        return true;
    }

    private static boolean isPng(byte[] data) {
        if (data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    private static boolean isPngComplete(byte[] data) {
        if (data.length < 12) return false;

        int searchStart = Math.max(0, data.length - 128);
        for (int i = data.length - 8; i >= searchStart; i--) {
            if (data[i] == PNG_IEND[0] &&
                    data[i + 1] == PNG_IEND[1] &&
                    data[i + 2] == PNG_IEND[2] &&
                    data[i + 3] == PNG_IEND[3]) {
                return (i + 4 + 4) <= data.length;
            }
        }
        return false;
    }

    private static boolean isJpeg(byte[] data) {
        return data.length >= 4 &&
                (data[0] & 0xFF) == 0xFF &&
                (data[1] & 0xFF) == 0xD8;
    }

    private static boolean isJpegComplete(byte[] data) {
        for (int i = data.length - 1; i >= Math.max(1, data.length - 64); i--) {
            if ((data[i - 1] & 0xFF) == 0xFF && (data[i] & 0xFF) == 0xD9) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGif(byte[] data) {
        return data.length >= 6 &&
                data[0] == 'G' && data[1] == 'I' && data[2] == 'F' &&
                data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    private static boolean isGifComplete(byte[] data) {
        for (int i = data.length - 1; i >= Math.max(0, data.length - 16); i--) {
            if (data[i] == 0x3B) return true;
        }
        return false;
    }

    private static boolean isWebP(byte[] data) {
        return data.length >= 12 &&
                data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
                data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static boolean isWebPComplete(byte[] data) {
        long riffLen = (data[4] & 0xFFL)
                | ((data[5] & 0xFFL) << 8)
                | ((data[6] & 0xFFL) << 16)
                | ((data[7] & 0xFFL) << 24);
        return data.length >= (riffLen + 8);
    }
}
