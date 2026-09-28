package dev.gdinizds.discordaibot.adapter.out.image;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordaibot.adapter.out.image.SafeImageDownloader.ImageRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeImageDownloaderTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private SafeImageDownloader permissive;

    @BeforeEach
    void setUp() {
        wireMock.start();
        permissive = new SafeImageDownloader(Duration.ofSeconds(2), 1024, "test", address -> true, false);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void downloadsAndDetectsTheFormatFromTheBytes() {
        wireMock.stubFor(get(urlPathEqualTo("/a.bin")).willReturn(aResponse().withBody(PNG)));

        var image = permissive.download(wireMock.baseUrl() + "/a.bin");

        assertThat(image.format()).isEqualTo(ImageFormat.PNG);
        assertThat(image.data()).isEqualTo(PNG);
    }

    @Test
    void followsRedirectsAndChecksEachHop() {
        wireMock.stubFor(get(urlPathEqualTo("/old")).willReturn(aResponse().withStatus(302).withHeader("Location", "/new.jpg")));
        wireMock.stubFor(get(urlPathEqualTo("/new.jpg")).willReturn(aResponse().withBody(JPEG)));

        assertThat(permissive.download(wireMock.baseUrl() + "/old").format()).isEqualTo(ImageFormat.JPEG);
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        wireMock.stubFor(get(urlPathEqualTo("/fake.png")).willReturn(aResponse()
                .withHeader("Content-Type", "image/png").withBody("<html>not an image</html>")));

        assertThatThrownBy(() -> permissive.download(wireMock.baseUrl() + "/fake.png"))
                .isInstanceOf(ImageRejectedException.class).hasMessageContaining("PNG, JPEG, GIF nem WEBP");
    }

    @Test
    void rejectsImagesAboveTheSizeLimit() {
        byte[] big = new byte[2048];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        wireMock.stubFor(get(urlPathEqualTo("/big.png")).willReturn(aResponse().withBody(big)));

        assertThatThrownBy(() -> permissive.download(wireMock.baseUrl() + "/big.png"))
                .isInstanceOf(ImageRejectedException.class).hasMessageContaining("limite");
    }

    @Test
    void rejectsNon200Responses() {
        wireMock.stubFor(get(urlPathEqualTo("/gone.png")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> permissive.download(wireMock.baseUrl() + "/gone.png"))
                .isInstanceOf(ImageRejectedException.class).hasMessage("HTTP 404");
    }

    @Test
    void defaultPolicyRefusesInternalDestinationsBeforeConnecting() {
        var strict = new SafeImageDownloader(Duration.ofSeconds(2), 1024, "test");

        assertThatThrownBy(() -> strict.download("http://127.0.0.1/a.png")).hasMessage("endereço não permitido");
        assertThatThrownBy(() -> strict.download("http://169.254.169.254/latest/meta-data")).hasMessage("endereço não permitido");
        assertThatThrownBy(() -> strict.download("http://10.152.183.10/x.png")).hasMessage("endereço não permitido");
        assertThatThrownBy(() -> strict.download(wireMock.baseUrl() + "/a.png")).hasMessage("porta não permitida");
        assertThatThrownBy(() -> strict.download("file:///etc/passwd")).hasMessage("esquema não permitido");
        assertThatThrownBy(() -> strict.download("http://user:pass@example.com/a.png")).hasMessage("URL inválida");
    }

    @Test
    void redirectToAnInternalAddressIsRefused() {
        var guarded = new SafeImageDownloader(Duration.ofSeconds(2), 1024, "test",
                address -> address.isLoopbackAddress() && !address.getHostAddress().equals("127.0.0.2"), false);
        wireMock.stubFor(get(urlPathEqualTo("/jump")).willReturn(aResponse().withStatus(301)
                .withHeader("Location", "http://127.0.0.2:" + wireMock.port() + "/secret.png")));

        assertThatThrownBy(() -> guarded.download(wireMock.baseUrl() + "/jump")).hasMessage("endereço não permitido");
    }

    @Test
    void publicAddressClassification() throws Exception {
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("192.168.1.10"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("172.16.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("100.64.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("0.0.0.0"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("fd00::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::ffff:10.0.0.1"))).isFalse();
    }
}
