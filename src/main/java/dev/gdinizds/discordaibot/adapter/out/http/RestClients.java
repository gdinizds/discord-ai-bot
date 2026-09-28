package dev.gdinizds.discordaibot.adapter.out.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

final class RestClients {

    private RestClients() {}

    static RestClient create(RestClient.Builder builder, String baseUrl, Duration timeout) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}

