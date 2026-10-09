package com.messaging.emailprovider.delivery;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Atomic, shared platform/company/domain per-second submission limits. */
@Component
public class EmailSendingRateLimiter {
  private static final String LUA =
      """
      for i=1,#KEYS do
        if tonumber(redis.call('GET',KEYS[i]) or '0') >= tonumber(ARGV[i]) then
          return 0
        end
      end
      for i=1,#KEYS do
        redis.call('INCR',KEYS[i])
        redis.call('EXPIRE',KEYS[i],3)
      end
      return 1
      """;

  private final StringRedisTemplate redis;
  private final int platform;
  private final int company;
  private final int domain;
  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

  public EmailSendingRateLimiter(
      StringRedisTemplate redis,
      @Value("${app.delivery.rate.platform-per-second:10}") int platform,
      @Value("${app.delivery.rate.company-per-second:2}") int company,
      @Value("${app.delivery.rate.domain-per-second:2}") int domain) {
    this.redis = redis;
    this.platform = platform;
    this.company = company;
    this.domain = domain;
  }

  public boolean permit(Long companyId, Long domainId) {
    if (companyId == null || domainId == null
        || platform <= 0 || company <= 0 || domain <= 0) {
      return false;
    }
    String tick = Long.toString(Instant.now().getEpochSecond());
    var keys = List.of(
        "unifydesk:email:rate:global:" + tick,
        "unifydesk:email:rate:company:" + companyId + ":" + tick,
        "unifydesk:email:rate:domain:" + domainId + ":" + tick);
    try {
      return Long.valueOf(1L).equals(
          redis.execute(script, keys, Integer.toString(platform),
              Integer.toString(company), Integer.toString(domain)));
    } catch (RuntimeException unavailable) {
      return false;
    }
  }
}
