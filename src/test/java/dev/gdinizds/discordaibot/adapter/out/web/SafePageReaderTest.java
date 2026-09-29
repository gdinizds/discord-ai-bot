package dev.gdinizds.discordaibot.adapter.out.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordaibot.application.port.out.PageReaderPort.PageRejectedException;
import dev.gdinizds.discordaibot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafePageReaderTest {

    private static final String ARTICLE = """
            <!doctype html>
            <html><head>
              <title> Java 25 chegou </title>
              <meta name="description" content="Resumo das novidades">
              <script>alert('x')</script>
            </head><body>
              <nav><a href="/">Início</a> <a href="/blog">Blog</a></nav>
              <article>
                <h1>Novidades do Java 25</h1>
                <p>O Java 25 é uma versão LTS com várias melhorias de desempenho e novas APIs estáveis.</p>
                <ul><li>Scoped values finais</li><li>Compact source files</li></ul>
                <pre>void main() {
                IO.println("oi");
            }</pre>
              </article>
              <footer>Copyright</footer>
            </body></html>
            """;

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Resilience resilience;
    private SafePageReader reader;

    @BeforeEach
    void setUp() {
        wireMock.start();
        resilience = new Resilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), executor);
        reader = new SafePageReader(Duration.ofSeconds(3), 64 * 1024, "test", resilience, a -> true, false);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void extractsTheMainContentOfAnHtmlPage() {
        wireMock.stubFor(get(urlPathEqualTo("/post")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html; charset=utf-8").withBody(ARTICLE)));

        var page = reader.read(wireMock.baseUrl() + "/post");

        assertThat(page.title()).isEqualTo("Java 25 chegou");
        assertThat(page.description()).isEqualTo("Resumo das novidades");
        assertThat(page.text())
                .contains("## Novidades do Java 25")
                .contains("versão LTS")
                .contains("- Scoped values finais")
                .contains("IO.println(\"oi\");")
                .doesNotContain("alert")
                .doesNotContain("Início")
                .doesNotContain("Copyright");
    }

    @Test
    void plainTextIsReturnedAsIs() {
        wireMock.stubFor(get(urlPathEqualTo("/README.md")).willReturn(aResponse()
                .withHeader("Content-Type", "text/plain; charset=utf-8").withBody("# Projeto\n\nComo rodar: ./gradlew test\n")));

        var page = reader.read(wireMock.baseUrl() + "/README.md");

        assertThat(page.title()).isEqualTo("README.md");
        assertThat(page.text()).isEqualTo("# Projeto\n\nComo rodar: ./gradlew test");
    }

    @Test
    void declaredCharsetIsHonoured() {
        wireMock.stubFor(get(urlPathEqualTo("/latin")).willReturn(aResponse()
                .withHeader("Content-Type", "text/plain; charset=ISO-8859-1")
                .withBody("ação".getBytes(StandardCharsets.ISO_8859_1))));

        assertThat(reader.read(wireMock.baseUrl() + "/latin").text()).isEqualTo("ação");
    }

    @Test
    void redirectsAreFollowedAndEachHopIsChecked() {
        wireMock.stubFor(get(urlPathEqualTo("/old")).willReturn(aResponse().withStatus(301).withHeader("Location", "/new")));
        wireMock.stubFor(get(urlPathEqualTo("/new")).willReturn(aResponse()
                .withHeader("Content-Type", "text/plain").withBody("novo endereço")));

        var page = reader.read(wireMock.baseUrl() + "/old");

        assertThat(page.url()).endsWith("/new");
        assertThat(page.text()).isEqualTo("novo endereço");
    }

    @Test
    void redirectLoopsStop() {
        wireMock.stubFor(get(urlPathEqualTo("/loop")).willReturn(aResponse().withStatus(302).withHeader("Location", "/loop")));

        assertThatThrownBy(() -> reader.read(wireMock.baseUrl() + "/loop"))
                .isInstanceOf(PageRejectedException.class)
                .hasMessageContaining("redirecionamentos");
    }

    @Test
    void binaryContentIsRejected() {
        wireMock.stubFor(get(urlPathEqualTo("/foto.png")).willReturn(aResponse()
                .withHeader("Content-Type", "image/png").withBody(new byte[]{1, 2, 3})));

        assertThatThrownBy(() -> reader.read(wireMock.baseUrl() + "/foto.png"))
                .isInstanceOf(PageRejectedException.class)
                .hasMessageContaining("image/png");
    }

    @Test
    void errorStatusIsReported() {
        wireMock.stubFor(get(urlPathEqualTo("/sumiu")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> reader.read(wireMock.baseUrl() + "/sumiu"))
                .isInstanceOf(PageRejectedException.class)
                .hasMessageContaining("404");
    }

    @Test
    void oversizedPagesAreCutAtTheByteLimit() {
        wireMock.stubFor(get(urlPathEqualTo("/grande")).willReturn(aResponse()
                .withHeader("Content-Type", "text/plain").withBody("a".repeat(200_000))));

        assertThat(reader.read(wireMock.baseUrl() + "/grande").text()).hasSize(64 * 1024);
    }

    @Test
    void internalAddressesAndOtherSchemesAreRefused() {
        var strict = new SafePageReader(Duration.ofSeconds(1), 1024, "test", resilience);

        assertThatThrownBy(() -> strict.read("http://127.0.0.1/admin")).isInstanceOf(PageRejectedException.class);
        assertThatThrownBy(() -> strict.read("http://169.254.169.254/latest/meta-data")).isInstanceOf(PageRejectedException.class);
        assertThatThrownBy(() -> strict.read("http://example.com:8080/x")).isInstanceOf(PageRejectedException.class);
        assertThatThrownBy(() -> strict.read("file:///etc/passwd")).isInstanceOf(PageRejectedException.class);
        assertThatThrownBy(() -> strict.read("https://user:pass@example.com/")).isInstanceOf(PageRejectedException.class);
        assertThatThrownBy(() -> strict.read("não é url")).isInstanceOf(PageRejectedException.class);
    }

    @Test
    void githubBlobLinksAreReadAsRawFiles() {
        assertThat(SafePageReader.rewrite("https://github.com/gdinizds/discord-ai-bot/blob/master/README.md"))
                .isEqualTo("https://raw.githubusercontent.com/gdinizds/discord-ai-bot/master/README.md");
        assertThat(SafePageReader.rewrite("<https://example.com/a>")).isEqualTo("https://example.com/a");
        assertThat(SafePageReader.rewrite("https://github.com/gdinizds/discord-ai-bot")).isEqualTo(
                "https://github.com/gdinizds/discord-ai-bot");
    }
}
