package dev.gdinizds.discordaibot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(AiBotProperties properties) {
        var s3 = properties.s3();
        AwsCredentialsProvider credentials = s3.accessKey() == null || s3.accessKey().isBlank()
                ? AnonymousCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        return S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .credentialsProvider(credentials)
                .region(Region.of(s3.region()))
                .forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(50)
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(s3.timeout())
                        .connectionAcquisitionTimeout(Duration.ofSeconds(5)))
                .build();
    }
}

