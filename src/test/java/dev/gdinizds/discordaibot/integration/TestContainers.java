package dev.gdinizds.discordaibot.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.GenericContainer;
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

    static final String S3_ACCESS_KEY = "test";
    static final String S3_SECRET_KEY = "test";

    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>("localstack/localstack:3.5")
            .withExposedPorts(4566)
            .withEnv("SERVICES", "s3")
            .withEnv("DEFAULT_REGION", "us-east-1");

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    private static boolean started;

    private TestContainers() {}

    static synchronized void start() {
        if (started) return;
        POSTGRES.start();
        REDPANDA.start();
        LOCALSTACK.start();
        WIREMOCK.start();
        createTopics();
        try (S3Client s3 = s3()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
        started = true;
    }

    static S3Client s3() {
        return S3Client.builder()
                .endpointOverride(URI.create(s3Url()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    static String s3Url() {
        return "http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566);
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

