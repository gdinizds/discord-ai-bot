package dev.gdinizds.discordaibot.adapter.out.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {

    private static final int TIMEOUT_MS = 3000;

    private final KafkaAdmin kafkaAdmin;
    private volatile AdminClient admin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try {
            String clusterId = admin().describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS))
                    .clusterId()
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return Health.up().withDetail("clusterId", clusterId).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    @Override
    public void destroy() {
        AdminClient current = admin;
        if (current != null) {
            current.close(Duration.ofSeconds(5));
        }
    }

    private AdminClient admin() {
        AdminClient current = admin;
        if (current == null) {
            synchronized (this) {
                current = admin;
                if (current == null) {
                    current = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                    admin = current;
                }
            }
        }
        return current;
    }
}
