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
    private static final long CACHE_TTL_MS = 5000L;

    private final KafkaAdmin kafkaAdmin;
    private volatile AdminClient admin;
    private volatile Health cachedHealth;
    private volatile long cacheExpiresAt;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        Health current = cachedHealth;
        if (current != null && now < cacheExpiresAt) {
            return current;
        }
        try {
            String clusterId = admin().describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS))
                    .clusterId()
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Health h = Health.up().withDetail("clusterId", clusterId).build();
            cachedHealth = h;
            cacheExpiresAt = now + CACHE_TTL_MS;
            return h;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Health h = Health.down(e).build();
            cachedHealth = h;
            cacheExpiresAt = now + 1000L;
            return h;
        } catch (Exception e) {
            Health h = Health.down(e).build();
            cachedHealth = h;
            cacheExpiresAt = now + 1000L;
            return h;
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
