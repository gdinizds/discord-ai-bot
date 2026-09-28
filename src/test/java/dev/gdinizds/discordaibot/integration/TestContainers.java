package dev.gdinizds.discordaibot.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

final class TestContainers {

    static final String DATABASE = "discord_ai_bot";
    static final String BUCKET = "discord-gateway-attachments";
    static final List<String> TOPICS = List.of(
            "discord.events.interaction.command",
            "discord.events.message.command",
            "discord.events.message.created",
            "discord.gateway.responses",
            "discord.gateway.commands");

    // Unnamed build: Testcontainers tags it localhost/testcontainers/<id>, which it treats as a
    // local image. A short explicit name is resolved against Docker Hub and fails with NotFound.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            new ImageFromDockerfile()
                    .withDockerfile(Paths.get("docker/postgres/Dockerfile"))
                    .get())
            .asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE))
            .withCommand("postgres",
                    "-c", "shared_preload_libraries=pg_cron",
                    "-c", "cron.database_name=" + DATABASE)
            .withDatabaseName(DATABASE);

    static final RedpandaContainer REDPANDA = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.18");

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    private static boolean started;

    private TestContainers() {}

    static synchronized void start() {
        if (started) return;
        POSTGRES.start();
        REDPANDA.start();
        MINIO.start();
        WIREMOCK.start();
        createTopics();
        try (S3Client s3 = s3()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
        started = true;
    }

    static S3Client s3() {
        return S3Client.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    private static void createTopics() {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers()))) {
            admin.createTopics(TOPICS.stream().map(t -> new NewTopic(t, 3, (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create topics", e);
        }
    }
}

