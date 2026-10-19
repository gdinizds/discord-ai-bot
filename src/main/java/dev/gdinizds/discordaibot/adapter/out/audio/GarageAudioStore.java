package dev.gdinizds.discordaibot.adapter.out.audio;

import dev.gdinizds.discordaibot.application.port.out.AudioStorePort;
import dev.gdinizds.discordaibot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class GarageAudioStore implements AudioStorePort {

    static final String INSTANCE = "garage";
    static final String FILE_NAME = "fala.mp3";
    static final String CONTENT_TYPE = "audio/mpeg";

    private static final Logger log = LoggerFactory.getLogger(GarageAudioStore.class);

    private final S3Client s3;
    private final Resilience resilience;
    private final String endpoint;
    private final String bucket;
    private final String keyPrefix;

    public GarageAudioStore(S3Client s3, Resilience resilience,
                            String endpoint, String bucket, String keyPrefix) {
        this.s3 = s3;
        this.resilience = resilience;
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String store(String guildId, String correlationId, byte[] audioData) {
        String targetGuild = guildId != null && !guildId.isBlank() ? guildId : "dm";
        String key = keyPrefix + "/" + targetGuild + "/" + correlationId + "/" + FILE_NAME;
        resilience.run(INSTANCE, () -> s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(CONTENT_TYPE)
                        .contentLength((long) audioData.length)
                        .build(),
                RequestBody.fromBytes(audioData)));
        String url = endpoint + "/" + bucket + "/" + key;
        log.debug("Stored audio at {}", url);
        return url;
    }
}
