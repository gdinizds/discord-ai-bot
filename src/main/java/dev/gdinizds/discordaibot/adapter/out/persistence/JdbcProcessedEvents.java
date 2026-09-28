package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.ProcessedEventPort;
import dev.gdinizds.discordaibot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

public class JdbcProcessedEvents implements ProcessedEventPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcProcessedEvents.class);

    private final JdbcClient jdbc;
    private final Resilience resilience;

    public JdbcProcessedEvents(JdbcClient jdbc, Resilience resilience) {
        this.jdbc = jdbc;
        this.resilience = resilience;
    }

    @Override
    public boolean tryAcquire(String correlationId) {
        try {
            int inserted = resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                            INSERT INTO ai_bot.processed_events (correlation_id) VALUES (:id)
                            ON CONFLICT (correlation_id) DO NOTHING
                            """)
                    .param("id", Ids.uuid(correlationId))
                    .update());
            return inserted == 1;
        } catch (RuntimeException e) {
            log.warn("database: dedup unavailable, processing event anyway: {}", e.toString());
            return true;
        }
    }

    @Scheduled(cron = "${ai-bot.processed-events.purge-cron:0 17 * * * *}")
    public void purge() {
        try {
            int removed = jdbc.sql("DELETE FROM ai_bot.processed_events WHERE processed_at < now() - interval '2 days'")
                    .update();
            if (removed > 0) log.info("Purged {} processed events", removed);
        } catch (RuntimeException e) {
            log.warn("database: processed_events purge failed: {}", e.toString());
        }
    }
}

