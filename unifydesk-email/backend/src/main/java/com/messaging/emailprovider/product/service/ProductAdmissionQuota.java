package com.messaging.emailprovider.product.service;

import com.messaging.emailprovider.common.exception.TooManyRequestsException;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Atomic fail-closed daily admission budget. Quota reservations intentionally remain
 * consumed if the subsequent database transaction fails, pending a reconciler.
 */
@Component
public class ProductAdmissionQuota {
  private static final String SCRIPT = """
      if redis.call('EXISTS',KEYS[3]) == 1 then return 2 end
      if tonumber(redis.call('GET',KEYS[1]) or '0') + tonumber(ARGV[1]) > tonumber(ARGV[2])
          or tonumber(redis.call('GET',KEYS[2]) or '0') + tonumber(ARGV[1]) > tonumber(ARGV[3])
      then return 0 end
      redis.call('INCRBY',KEYS[1],ARGV[1])
      redis.call('EXPIRE',KEYS[1],172800)
      redis.call('INCRBY',KEYS[2],ARGV[1])
      redis.call('EXPIRE',KEYS[2],172800)
      redis.call('SET',KEYS[3],'1','EX',172800)
      return 1
      """;

  private final StringRedisTemplate redis;
  private final long projectLimit;
  private final long platformLimit;
  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT, Long.class);

  public ProductAdmissionQuota(
      StringRedisTemplate redis,
      @Value("${app.product.quotas.project-daily:1000}") long projectLimit,
      @Value("${app.product.quotas.platform-daily:10000}") long platformLimit) {
    this.redis = redis;
    this.projectLimit = projectLimit;
    this.platformLimit = platformLimit;
  }

  public void reserve(Long projectId, String idempotencyId, int recipients) {
    if (projectId == null || idempotencyId == null || idempotencyId.isBlank()
        || recipients <= 0 || projectLimit <= 0 || platformLimit <= 0) {
      throw new TooManyRequestsException("Invalid or disabled email admission quota");
    }
    String date = LocalDate.now(ZoneOffset.UTC).toString();
    List<String> keys = List.of(
        "ud:email:quota:project:" + projectId + ":" + date,
        "ud:email:quota:platform:" + date,
        "ud:email:quota:reservation:" + projectId + ":"
            + ApiKeyCrypto.hash(idempotencyId));
    Long result;
    try {
      result = redis.execute(script, keys, Integer.toString(recipients),
          Long.toString(projectLimit), Long.toString(platformLimit));
    } catch (RuntimeException e) {
      throw new TooManyRequestsException("Email quota service is unavailable");
    }
    if (result == null || result == 0L) {
      throw new TooManyRequestsException("Daily email admission budget exceeded");
    }
  }
}
