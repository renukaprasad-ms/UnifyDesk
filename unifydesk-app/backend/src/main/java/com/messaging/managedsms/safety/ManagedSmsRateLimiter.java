package com.messaging.managedsms.safety;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsRateLimiter {

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
          local now = tonumber(ARGV[1])
          local rate = tonumber(ARGV[2])
          if rate == nil or rate <= 0 then return 0 end
          for i = 1, 4 do
            local data = redis.call('HMGET', KEYS[i], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = math.max(1, rate) end
            if ts == nil then ts = now end
            local elapsed = math.max(0, now - ts)
            tokens = math.min(math.max(1, rate), tokens + (elapsed * rate / 1000.0))
            if tokens < 1 then return 0 end
          end
          if ARGV[3] == '1' then
            local hourCap = tonumber(ARGV[4])
            local dayCap = tonumber(ARGV[5])
            if hourCap == nil or dayCap == nil or hourCap <= 0 or dayCap <= 0 then return 0 end
            local hourCount = tonumber(redis.call('GET', KEYS[5]) or '0')
            local dayCount = tonumber(redis.call('GET', KEYS[6]) or '0')
            if hourCount + 1 > hourCap or dayCount + 1 > dayCap then return 0 end
          end
          for i = 1, 4 do
            local data = redis.call('HMGET', KEYS[i], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = math.max(1, rate) end
            if ts == nil then ts = now end
            local elapsed = math.max(0, now - ts)
            tokens = math.min(math.max(1, rate), tokens + (elapsed * rate / 1000.0))
            redis.call('HMSET', KEYS[i], 'tokens', tokens - 1, 'ts', now)
            redis.call('PEXPIRE', KEYS[i], 60000)
          end
          if ARGV[3] == '1' then
            redis.call('INCR', KEYS[5])
            redis.call('EXPIRE', KEYS[5], 7200)
            redis.call('INCR', KEYS[6])
            redis.call('EXPIRE', KEYS[6], 172800)
          end
          return 1
          """,
          Long.class);

  private static final DateTimeFormatter HOUR =
      DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final StringRedisTemplate redisTemplate;
  private final ManagedSmsRuntimeProperties properties;

  public boolean tryAcquire(
      ManagedSmsRuntimeRepository.DeliveryRuntime runtime, Long runId) {
    if (runtime == null || !properties.providerConfigured()) return false;
    double rate = properties.getGlobalMaxPerSecond();
    if (rate <= 0) return false;
    boolean newTenant =
        runtime.tenantCreatedAt() == null
            || runtime.tenantCreatedAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS));
    Instant now = Instant.now();
    List<String> keys =
        List.of(
            "unifydesk:managed-sms:rate:global",
            "unifydesk:managed-sms:rate:account:" + safe(runtime.providerAccountKey()),
            "unifydesk:managed-sms:rate:company:" + runtime.companyId(),
            "unifydesk:managed-sms:rate:run:" + runId,
            "unifydesk:managed-sms:new-tenant:hour:" + runtime.companyId() + ":" + HOUR.format(now),
            "unifydesk:managed-sms:new-tenant:day:" + runtime.companyId() + ":" + DAY.format(now));
    try {
      Long allowed =
          redisTemplate.execute(
              SCRIPT,
              keys,
              Long.toString(System.currentTimeMillis()),
              Double.toString(rate),
              newTenant ? "1" : "0",
              Long.toString(properties.getNewTenantHourlyCap()),
              Long.toString(properties.getNewTenantDailyCap()));
      return Long.valueOf(1L).equals(allowed);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "master" : value.replaceAll("[^A-Za-z0-9._:-]", "_");
  }
}
