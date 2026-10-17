package dev.gdinizds.discordaibot.adapter.out.s3;

import dev.gdinizds.discordaibot.application.port.out.AttachmentFetcherPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.InputAttachment;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class GarageAttachmentFetcher implements AttachmentFetcherPort {

    static final String INSTANCE = "garage";

    private final S3Client s3;
    private final Resilience resilience;
    private final String expectedBucket;
    private final long maxBytes;

    public GarageAttachmentFetcher(S3Client s3, Resilience resilience) {
        this(s3, resilience, null, 10_485_760L);
    }

    public GarageAttachmentFetcher(S3Client s3, Resilience resilience, String expectedBucket, long maxBytes) {
        this.s3 = s3;
        this.resilience = resilience;
        this.expectedBucket = expectedBucket;
        this.maxBytes = maxBytes;
    }

    @Override
    public byte[] fetch(InputAttachment attachment) {
        ObjectLocation location = parse(attachment.objectKey());
        if (expectedBucket != null && !expectedBucket.isBlank() && !expectedBucket.equals(location.bucket())) {
            throw new IllegalArgumentException("Acesso negado ao bucket: " + location.bucket());
        }
        return resilience.call(INSTANCE, () -> {
            try (var response = s3.getObject(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build())) {
                byte[] data = response.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
                if (data.length > maxBytes) {
                    throw new IllegalArgumentException("Anexo excede o tamanho máximo permitido de " + maxBytes + " bytes");
                }
                return data;
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler o anexo do S3", e);
            }
        });
    }

    static ObjectLocation parse(String url) {
        String path = url;
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            if (slash < 0) throw new IllegalArgumentException("Attachment URL without path: " + url);
            path = path.substring(slash + 1);
        }
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int split = path.indexOf('/');
        if (split <= 0 || split == path.length() - 1) {
            throw new IllegalArgumentException("Attachment URL without bucket and key: " + url);
        }
        String key = path.substring(split + 1);
        if (key.contains("%")) key = URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
        return new ObjectLocation(path.substring(0, split), key);
    }

    record ObjectLocation(String bucket, String key) {}
}

