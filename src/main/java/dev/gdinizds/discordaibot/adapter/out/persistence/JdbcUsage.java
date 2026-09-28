package dev.gdinizds.discordaibot.adapter.out.persistence;

import dev.gdinizds.discordaibot.application.port.out.UsagePort;
import dev.gdinizds.discordaibot.config.Resilience;
import dev.gdinizds.discordaibot.domain.model.TokenUsage;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

public class JdbcUsage implements UsagePort {

    private final JdbcClient jdbc;
    private final Resilience resilience;

    public JdbcUsage(JdbcClient jdbc, Resilience resilience) {
        this.jdbc = jdbc;
        this.resilience = resilience;
    }

    @Override
    public UserDay userDay(String userId, LocalDate day) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT requests, cost_micro_usd FROM ai_bot.usage_daily
                         WHERE day = :day AND user_id = :user
                        """)
                .param("day", day)
                .param("user", Ids.snowflake(userId))
                .query((rs, n) -> new UserDay(rs.getInt("requests"), rs.getLong("cost_micro_usd")))
                .optional()
                .orElse(UserDay.ZERO));
    }

    @Override
    public long costMicroUsdBetween(LocalDate from, LocalDate toExclusive) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT coalesce(sum(cost_micro_usd), 0) FROM ai_bot.usage_daily
                         WHERE day >= :from AND day < :to
                        """)
                .param("from", from)
                .param("to", toExclusive)
                .query(Long.class)
                .single());
    }

    @Override
    public long userCostMicroUsdBetween(String userId, LocalDate from, LocalDate toExclusive) {
        return resilience.call(Instances.DATABASE, () -> jdbc.sql("""
                        SELECT coalesce(sum(cost_micro_usd), 0) FROM ai_bot.usage_daily
                         WHERE user_id = :user AND day >= :from AND day < :to
                        """)
                .param("user", Ids.snowflake(userId))
                .param("from", from)
                .param("to", toExclusive)
                .query(Long.class)
                .single());
    }

    @Override
    public void add(String userId, LocalDate day, TokenUsage usage, long costMicroUsd) {
        resilience.run(Instances.DATABASE, () -> jdbc.sql("""
                        INSERT INTO ai_bot.usage_daily
                               (day, user_id, requests, input_tokens, output_tokens, cost_micro_usd)
                        VALUES (:day, :user, 1, :input, :output, :cost)
                        ON CONFLICT (day, user_id) DO UPDATE SET
                               requests       = usage_daily.requests + 1,
                               input_tokens   = usage_daily.input_tokens + EXCLUDED.input_tokens,
                               output_tokens  = usage_daily.output_tokens + EXCLUDED.output_tokens,
                               cost_micro_usd = usage_daily.cost_micro_usd + EXCLUDED.cost_micro_usd,
                               updated_at     = now()
                        """)
                .param("day", day)
                .param("user", Ids.snowflake(userId))
                .param("input", (long) usage.input())
                .param("output", (long) usage.output())
                .param("cost", costMicroUsd)
                .update());
    }
}
