package dev.gdinizds.discordaibot.adapter.out.image;

import dev.gdinizds.discordaibot.application.port.out.ImageStorePort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class GarageImageStore implements ImageStorePort {

    static final String INSTANCE = "garage";
    static final int MAX_DESCRIPTION = 200;

    private final SafeImageDownloader downloader;
    private final S3Client s3;
    private final Resilience resilience;
    private final String endpoint;
    private final String bucket;
    private final String keyPrefix;

    public GarageImageStore(SafeImageDownloader downloader, S3Client s3, Resilience resilience,
                            String endpoint, String bucket, String keyPrefix) {
        this.downloader = downloader;
        this.s3 = s3;
        this.resilience = resilience;
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public OutboundImage store(String guildId, String correlationId, int index, ImageHit hit) {
        SafeImageDownloader.DownloadedImage image = downloader.download(hit.imageUrl());
        String fileName = "imagem-" + index + "." + image.format().extension();
        String key = keyPrefix + "/" + guildId + "/" + correlationId + "/" + fileName;
        resilience.run(INSTANCE, () -> s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(image.format().mimeType())
                        .contentLength((long) image.data().length)
                        .build(),
                RequestBody.fromBytes(image.data())));
        return new OutboundImage(endpoint + "/" + bucket + "/" + key, fileName, description(hit));
    }

    static String description(ImageHit hit) {
        String text = hit.title() == null ? "" : hit.title().replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) return null;
        if (text.length() <= MAX_DESCRIPTION) return text;
        int end = MAX_DESCRIPTION - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "…";
    }
}
