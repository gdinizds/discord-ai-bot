package dev.gdinizds.discordaibot.adapter.out.image;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Predicate;

public class SafeImageDownloader {

    static final int MAX_REDIRECTS = 3;

    private final HttpClient client;
    private final Duration timeout;
    private final long maxBytes;
    private final String userAgent;
    private final Predicate<InetAddress> addressAllowed;
    private final boolean standardPortsOnly;

    public SafeImageDownloader(Duration timeout, long maxBytes, String userAgent) {
        this(timeout, maxBytes, userAgent, SafeImageDownloader::isPublicAddress, true);
    }

    SafeImageDownloader(Duration timeout, long maxBytes, String userAgent,
                        Predicate<InetAddress> addressAllowed, boolean standardPortsOnly) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.userAgent = userAgent;
        this.addressAllowed = addressAllowed;
        this.standardPortsOnly = standardPortsOnly;
    }

    public DownloadedImage download(String url) {
        URI uri = parse(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkDestination(uri);
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ImageRejectedException("redirect sem Location"));
                closeQuietly(response.body());
                uri = parse(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new ImageRejectedException("HTTP " + status);
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > maxBytes) {
                closeQuietly(response.body());
                throw new ImageRejectedException("maior que o limite de " + maxBytes + " bytes");
            }
            byte[] data = readLimited(response.body());
            ImageFormat format = ImageFormat.sniff(data)
                    .orElseThrow(() -> new ImageRejectedException("conteúdo não é PNG, JPEG, GIF nem WEBP"));
            return new DownloadedImage(data, format);
        }
        throw new ImageRejectedException("redirecionamentos demais");
    }

    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if (first == 0 || first >= 224) return false;
            if (first == 100 && second >= 64 && second <= 127) return false;
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return false;
            if (first == 198 && (second == 18 || second == 19)) return false;
            return true;
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc;
        }
        return false;
    }

    private void checkDestination(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new ImageRejectedException("host desconhecido");
        }
        for (InetAddress address : addresses) {
            if (!addressAllowed.test(address)) {
                throw new ImageRejectedException("endereço não permitido");
            }
        }
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "image/png,image/jpeg,image/gif,image/webp")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageRejectedException("interrompido");
        } catch (IOException e) {
            throw new ImageRejectedException("falha de rede: " + e.getClass().getSimpleName());
        }
    }

    private byte[] readLimited(InputStream body) {
        try (body) {
            byte[] data = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
            if (data.length > maxBytes) {
                throw new ImageRejectedException("maior que o limite de " + maxBytes + " bytes");
            }
            return data;
        } catch (IOException e) {
            throw new ImageRejectedException("falha ao ler a imagem");
        }
    }

    private URI parse(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new ImageRejectedException("URL inválida");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ImageRejectedException("esquema não permitido");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
            throw new ImageRejectedException("URL inválida");
        }
        int port = uri.getPort();
        if (standardPortsOnly && port != -1 && port != 80 && port != 443) {
            throw new ImageRejectedException("porta não permitida");
        }
        return uri;
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
        }
    }

    public record DownloadedImage(byte[] data, ImageFormat format) {}

    public static class ImageRejectedException extends RuntimeException {
        public ImageRejectedException(String reason) {
            super(reason);
        }
    }
}
