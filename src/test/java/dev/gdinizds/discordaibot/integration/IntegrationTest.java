package dev.gdinizds.discordaibot.integration;

import dev.gdinizds.discordaibot.support.TestLlmConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@RequiresDocker
@SpringBootTest
@ActiveProfiles("test")
@Import(TestLlmConfig.class)
abstract class IntegrationTest {

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        TestContainers.start();
        registry.add("spring.datasource.url", TestContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestContainers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", TestContainers.REDPANDA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "discord-ai-bot-it");
        registry.add("ai-bot.s3.endpoint", TestContainers.MINIO::getS3URL);
        registry.add("ai-bot.s3.access-key", TestContainers.MINIO::getUserName);
        registry.add("ai-bot.s3.secret-key", TestContainers.MINIO::getPassword);
        registry.add("ai-bot.s3.bucket", () -> TestContainers.BUCKET);
        registry.add("ai-bot.s3.region", () -> "us-east-1");
        registry.add("ai-bot.tools.searxng-url", TestContainers.WIREMOCK::baseUrl);
        registry.add("ai-bot.tools.open-meteo-geocoding-url", TestContainers.WIREMOCK::baseUrl);
        registry.add("ai-bot.tools.open-meteo-forecast-url", TestContainers.WIREMOCK::baseUrl);
        registry.add("ai-bot.tools.wikipedia-url", TestContainers.WIREMOCK::baseUrl);
        registry.add("ai-bot.reply.placeholder-min-delay", () -> "300ms");
    }
}

