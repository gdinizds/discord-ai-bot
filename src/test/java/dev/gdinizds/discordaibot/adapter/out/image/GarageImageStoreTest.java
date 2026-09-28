package dev.gdinizds.discordaibot.adapter.out.image;

import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GarageImageStoreTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final S3Client s3 = mock(S3Client.class);
    private final Resilience resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
            TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void uploadsUnderThePrefixAndReturnsTheGarageUrl() {
        var downloader = new SafeImageDownloader(Duration.ofSeconds(1), 1024, "test", a -> true, false) {
            @Override
            public DownloadedImage download(String url) {
                return new DownloadedImage(PNG, ImageFormat.PNG);
            }
        };
        var store = new GarageImageStore(downloader, s3, resilience,
                "http://garage.garage.svc.cluster.local:3900/", "discord-gateway-attachments", "ai-bot");

        var image = store.store("111", "cid-1", 2, new ImageHit("  Capivara   no lago ", "https://i/x", "https://p", "W", ""));

        assertThat(image.url()).isEqualTo(
                "http://garage.garage.svc.cluster.local:3900/discord-gateway-attachments/ai-bot/111/cid-1/imagem-2.png");
        assertThat(image.fileName()).isEqualTo("imagem-2.png");
        assertThat(image.description()).isEqualTo("Capivara no lago");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("discord-gateway-attachments");
        assertThat(request.getValue().key()).isEqualTo("ai-bot/111/cid-1/imagem-2.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(PNG.length);
    }

    @Test
    void rejectedDownloadNeverReachesTheBucket() {
        var downloader = new SafeImageDownloader(Duration.ofSeconds(1), 1024, "test");
        var store = new GarageImageStore(downloader, s3, resilience, "http://garage:3900", "b", "ai-bot");

        assertThatThrownBy(() -> store.store("1", "c", 1, new ImageHit("t", "http://127.0.0.1/x.png", "", "", "")))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void longTitlesAreCutForTheDiscordDescription() {
        String description = GarageImageStore.description(new ImageHit("a".repeat(500), "", "", "", ""));

        assertThat(description).hasSize(GarageImageStore.MAX_DESCRIPTION).endsWith("…");
        assertThat(GarageImageStore.description(new ImageHit("   ", "", "", "", ""))).isNull();
    }
}
