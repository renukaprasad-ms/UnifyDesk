package com.messaging.emailprovider.validation.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class DomainProbeLimiter {

  private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local rate = redis.call('INCR', KEYS[1])
          if rate == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
          end
          if rate > tonumber(ARGV[2]) then
            return 0
          end

          local concurrent = redis.call('INCR', KEYS[2])
          if concurrent == 1 then
            redis.call('EXPIRE', KEYS[2], ARGV[3])
          end
          if concurrent > tonumber(ARGV[4]) then
            redis.call('DECR', KEYS[2])
            return 0
          end
          return 1
          """,
          Long.class);

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local current = tonumber(redis.call('GET', KEYS[1]) or '0')
          if current <= 1 then
            redis.call('DEL', KEYS[1])
            return 0
          end
          return redis.call('DECR', KEYS[1])
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final int probesPerMinute;
  private final int perDomainConcurrency;
  private final int concurrencyLeaseSeconds;

  public DomainProbeLimiter(
      StringRedisTemplate redis,
      @Value("${app.validation.smtp.probes-per-domain-per-minute:20}") int probesPerMinute,
      @Value("${app.validation.smtp.per-domain-concurrency:2}") int perDomainConcurrency,
      @Value("${app.validation.smtp.concurrency-lease-seconds:20}") int concurrencyLeaseSeconds) {
    this.redis = redis;
    this.probesPerMinute = Math.max(1, probesPerMinute);
    this.perDomainConcurrency = Math.max(1, perDomainConcurrency);
    this.concurrencyLeaseSeconds = Math.max(5, concurrencyLeaseSeconds);
  }

  public Lease tryAcquire(String domain) {
    String safeDomain = domain.toLowerCase();
    String rateKey = "email-validator:domain-rate:" + safeDomain;
    String concurrencyKey = "email-validator:domain-concurrency:" + safeDomain;

    try {
      Long acquired =
          redis.execute(
              ACQUIRE_SCRIPT,
              List.of(rateKey, concurrencyKey),
              "60",
              Integer.toString(probesPerMinute),
              Integer.toString(concurrencyLeaseSeconds),
              Integer.toString(perDomainConcurrency));
      return Long.valueOf(1L).equals(acquired)
          ? new Lease(redis, concurrencyKey, true)
          : Lease.rejected();
    } catch (RuntimeException exception) {
      return Lease.rejected();
    }
  }

  public static final class Lease implements AutoCloseable {
    private final StringRedisTemplate redis;
    private final String concurrencyKey;
    private final boolean acquired;
    private boolean closed;

    private Lease(StringRedisTemplate redis, String concurrencyKey, boolean acquired) {
      this.redis = redis;
      this.concurrencyKey = concurrencyKey;
      this.acquired = acquired;
    }

    static Lease rejected() {
      return new Lease(null, null, false);
    }

    public boolean acquired() {
      return acquired;
    }

    @Override
    public void close() {
      if (!acquired || closed) {
        return;
      }
      closed = true;
      try {
        redis.execute(RELEASE_SCRIPT, List.of(concurrencyKey));
      } catch (RuntimeException ignored) {
        // Lease TTL provides crash safety if Redis release is temporarily unavailable.
      }
    }
  }
}
