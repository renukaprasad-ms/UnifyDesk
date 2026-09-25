package com.messaging.emailprovider.validation.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DomainProbeLimiter {

  private final StringRedisTemplate redis;
  private final Map<String, Semaphore> local = new ConcurrentHashMap<>();
  private final int perMinute;
  private final int localConcurrency;
  private final long acquireTimeoutMs;

  public DomainProbeLimiter(
      StringRedisTemplate redis,
      @Value("${app.validation.smtp.probes-per-domain-per-minute:20}") int perMinute,
      @Value("${app.validation.smtp.per-domain-concurrency:2}") int localConcurrency,
      @Value("${app.validation.smtp.acquire-timeout-ms:1500}") long acquireTimeoutMs) {
    this.redis = redis;
    this.perMinute = Math.max(1, perMinute);
    this.localConcurrency = Math.max(1, localConcurrency);
    this.acquireTimeoutMs = Math.max(1, acquireTimeoutMs);
  }

  public Lease tryAcquire(String domain) {
    Semaphore semaphore =
        local.computeIfAbsent(domain, ignored -> new Semaphore(localConcurrency, true));
    boolean localAcquired;
    try {
      localAcquired = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Lease.rejected();
    }
    if (!localAcquired) {
      return Lease.rejected();
    }

    try {
      String key = "email-validator:domain-rate:" + domain;
      Long count = redis.opsForValue().increment(key);
      if (count != null && count == 1) {
        redis.expire(key, Duration.ofMinutes(1));
      }
      if (count == null || count > perMinute) {
        semaphore.release();
        return Lease.rejected();
      }
      return new Lease(true, semaphore);
    } catch (RuntimeException exception) {
      semaphore.release();
      return Lease.rejected();
    }
  }

  public static final class Lease implements AutoCloseable {
    private final boolean acquired;
    private final Semaphore semaphore;

    private Lease(boolean acquired, Semaphore semaphore) {
      this.acquired = acquired;
      this.semaphore = semaphore;
    }

    static Lease rejected() {
      return new Lease(false, null);
    }

    public boolean acquired() {
      return acquired;
    }

    @Override
    public void close() {
      if (acquired && semaphore != null) {
        semaphore.release();
      }
    }
  }
}
