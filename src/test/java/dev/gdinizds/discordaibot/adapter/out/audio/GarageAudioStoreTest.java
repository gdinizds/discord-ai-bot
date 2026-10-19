package dev.gdinizds.discordaibot.adapter.out.audio;

import dev.gdinizds.discordaibot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GarageAudioStoreTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final S3Client s3 = mock(S3Client.class);
    private final Resilience resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
            TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void uploadsAudioUnderPrefixAndReturnsUrl() {
        var store = new GarageAudioStore(s3, resilience,
                "http://garage:3900/", "discord-gateway-attachments", "ai-bot");

        byte[] audioBytes = new byte[]{1, 2, 3, 4, 5};
        String url = store.store("900000000000000001", "cid-123", audioBytes);

        assertThat(url).isEqualTo("http://garage:3900/discord-gateway-attachments/ai-bot/900000000000000001/cid-123/fala.mp3");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(reqCaptor.capture(), any(RequestBody.class));

        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("discord-gateway-attachments");
        assertThat(req.key()).isEqualTo("ai-bot/900000000000000001/cid-123/fala.mp3");
        assertThat(req.contentType()).isEqualTo("audio/mpeg");
        assertThat(req.contentLength()).isEqualTo(5L);
    }

    @Test
    void uploadsDmAudioWithDmFolder() {
        var store = new GarageAudioStore(s3, resilience,
                "http://garage:3900", "discord-gateway-attachments", "ai-bot");

        byte[] audioBytes = new byte[]{10, 20};
        String url = store.store(null, "cid-456", audioBytes);

        assertThat(url).isEqualTo("http://garage:3900/discord-gateway-attachments/ai-bot/dm/cid-456/fala.mp3");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(reqCaptor.capture(), any(RequestBody.class));

        assertThat(reqCaptor.getValue().key()).isEqualTo("ai-bot/dm/cid-456/fala.mp3");
    }
}
