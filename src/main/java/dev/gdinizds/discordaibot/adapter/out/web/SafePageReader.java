package dev.gdinizds.discordaibot.adapter.out.web;

import dev.gdinizds.discordaibot.adapter.out.image.SafeImageDownloader;
import dev.gdinizds.discordaibot.application.port.out.PageReaderPort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.WebPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafePageReader implements PageReaderPort {

    public static final String INSTANCE = "web-page";
    static final int MAX_REDIRECTS = 5;
    static final int MIN_BLOCK_TEXT = 300;

    private static final Set<String> TEXT_TYPES = Set.of(
            "application/json", "application/xml", "application/x-yaml", "application/yaml",
            "application/javascript", "application/x-sh");
    private static final Pattern GITHUB_BLOB = Pattern.compile(
            "^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$");
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w.:-]+)\"?", Pattern.CASE_INSENSITIVE);

    private final HttpClient client;
    private final Duration timeout;
    private final long maxBytes;
    private final String userAgent;
    private final Predicate<InetAddress> addressAllowed;
    private final boolean standardPortsOnly;
    private final Resilience resilience;

    public SafePageReader(Duration timeout, long maxBytes, String userAgent, Resilience resilience) {
        this(timeout, maxBytes, userAgent, resilience, SafeImageDownloader::isPublicAddress, true);
    }

    SafePageReader(Duration timeout, long maxBytes, String userAgent, Resilience resilience,
                   Predicate<InetAddress> addressAllowed, boolean standardPortsOnly) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.userAgent = userAgent;
        this.resilience = resilience;
        this.addressAllowed = addressAllowed;
        this.standardPortsOnly = standardPortsOnly;
    }

    @Override
    public WebPage read(String url) {
        URI uri = parse(rewrite(url));
        try {
            return resilience.call(INSTANCE, () -> fetch(uri));
        } catch (Resilience.ExternalCallException e) {
            if (e.getCause() instanceof PageRejectedException rejected) throw rejected;
            throw e;
        }
    }

    private WebPage fetch(URI start) {
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkDestination(uri);
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new PageRejectedException("redirecionamento sem destino"));
                closeQuietly(response.body());
                uri = parse(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new PageRejectedException("a página respondeu HTTP " + status);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            String mime = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            boolean html = mime.equals("text/html") || mime.equals("application/xhtml+xml");
            if (!html && !mime.startsWith("text/") && !TEXT_TYPES.contains(mime)) {
                closeQuietly(response.body());
                throw new PageRejectedException("tipo de conteúdo não suportado: " + mime);
            }
            byte[] data = readLimited(response.body());
            Optional<Charset> charset = charset(contentType);
            return html ? fromHtml(uri, data, charset.orElse(null)) : fromText(uri, data, charset.orElse(StandardCharsets.UTF_8));
        }
        throw new PageRejectedException("redirecionamentos demais");
    }

    static WebPage fromHtml(URI uri, byte[] data, Charset charset) {
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(data), charset == null ? null : charset.name(), uri.toString());
        } catch (IOException e) {
            throw new PageRejectedException("não foi possível interpretar o HTML");
        }
        String title = doc.title().strip();
        String description = Optional.ofNullable(doc.selectFirst("meta[name=description], meta[property=og:description]"))
                .map(meta -> meta.attr("content").strip())
                .orElse("");
        doc.select("script, style, noscript, template, svg, iframe, nav, footer, header, aside, form, button").remove();

        Element root = Optional.ofNullable(doc.selectFirst("article"))
                .or(() -> Optional.ofNullable(doc.selectFirst("main")))
                .or(() -> Optional.ofNullable(doc.selectFirst("[role=main]")))
                .orElse(doc.body());
        String text = root == null ? "" : blocks(root);
        if (text.length() < MIN_BLOCK_TEXT && doc.body() != null) {
            String fallback = doc.body().text().strip();
            if (fallback.length() > text.length() * 2) text = fallback;
        }
        return new WebPage(uri.toString(), title, description, text);
    }

    static WebPage fromText(URI uri, byte[] data, Charset charset) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new WebPage(uri.toString(), name, "", new String(data, charset).strip());
    }

    private static String blocks(Element root) {
        StringBuilder sb = new StringBuilder();
        for (Element block : root.select("h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, td, th, dt, dd")) {
            if (nestedInBlock(block, root)) continue;
            String line = block.tagName().equals("pre") ? block.wholeText().strip() : block.text().strip();
            if (line.isEmpty()) continue;
            if (block.tagName().matches("h[1-6]")) sb.append("\n## ");
            else if (block.tagName().equals("li")) sb.append("- ");
            sb.append(line).append('\n');
        }
        return sb.toString().strip();
    }

    private static boolean nestedInBlock(Element block, Element root) {
        for (Element parent = block.parent(); parent != null && parent != root; parent = parent.parent()) {
            if (isBlock(parent)) return true;
        }
        return false;
    }

    private static boolean isBlock(Element element) {
        return switch (element.tagName()) {
            case "p", "li", "pre", "blockquote", "td", "th", "dt", "dd" -> true;
            default -> false;
        };
    }

    static String rewrite(String url) {
        if (url == null) return "";
        String trimmed = url.strip();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) trimmed = trimmed.substring(1, trimmed.length() - 1);
        Matcher blob = GITHUB_BLOB.matcher(trimmed);
        if (blob.matches()) {
            return "https://raw.githubusercontent.com/" + blob.group(1) + "/" + blob.group(2) + "/" + blob.group(3);
        }
        return trimmed;
    }

    private void checkDestination(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new PageRejectedException("site não encontrado");
        }
        for (InetAddress address : addresses) {
            if (!addressAllowed.test(address)) throw new PageRejectedException("endereço não permitido");
        }
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PageRejectedException("leitura interrompida");
        } catch (IOException e) {
            throw new IllegalStateException("falha de rede ao abrir " + uri.getHost() + ": " + e.getClass().getSimpleName(), e);
        }
    }

    private byte[] readLimited(InputStream body) {
        try (body) {
            return body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes));
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler a página", e);
        }
    }

    private URI parse(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new PageRejectedException("URL inválida");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new PageRejectedException("só links http e https");
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
            throw new PageRejectedException("URL inválida");
        }
        int port = uri.getPort();
        if (standardPortsOnly && port != -1 && port != 80 && port != 443) {
            throw new PageRejectedException("porta não permitida");
        }
        return uri;
    }

    private static Optional<Charset> charset(String contentType) {
        Matcher matcher = CHARSET.matcher(contentType);
        if (!matcher.find()) return Optional.empty();
        try {
            return Optional.of(Charset.forName(matcher.group(1)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
        }
    }
}
