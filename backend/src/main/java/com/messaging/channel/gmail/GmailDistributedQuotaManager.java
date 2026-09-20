package com.messaging.channel.gmail;

import com.messaging.redis.exception.RedisOperationException;
import com.messaging.redis.key.RedisKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailDistributedQuotaManager {

  private static final DefaultRedisScript<String> RESERVE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local cooldown = redis.call('PTTL', KEYS[1])
          if cooldown ~= nil and cooldown > 0 then
            return '0:' .. tostring(cooldown) .. ':cooldown'
          end

          local now = tonumber(ARGV[1])
          local cost = tonumber(ARGV[2])
          local ttl = tonumber(ARGV[3])
          local max_wait = 0
          local states = {}

          for i = 2, #KEYS do
            local arg_base = 4 + ((i - 2) * 2)
            local capacity = tonumber(ARGV[arg_base])
            local refill_per_ms = tonumber(ARGV[arg_base + 1])
            local tokens = tonumber(redis.call('HGET', KEYS[i], 'tokens'))
            local last = tonumber(redis.call('HGET', KEYS[i], 'last'))

            if tokens == nil then tokens = capacity end
            if last == nil then last = now end

            local elapsed = now - last
            if elapsed < 0 then elapsed = 0 end
            tokens = math.min(capacity, tokens + (elapsed * refill_per_ms))
            states[i] = tokens

            if tokens < cost then
              local wait_ms
              if refill_per_ms <= 0 then
                wait_ms = ttl
              else
                wait_ms = math.ceil((cost - tokens) / refill_per_ms)
              end
              if wait_ms > max_wait then max_wait = wait_ms end
            end
          end

          for i = 2, #KEYS do
            redis.call('HSET', KEYS[i], 'tokens', states[i], 'last', now)
            redis.call('PEXPIRE', KEYS[i], ttl)
          end

          if max_wait > 0 then
            return '0:' .. tostring(max_wait) .. ':quota'
          end

          for i = 2, #KEYS do
            redis.call('HINCRBYFLOAT', KEYS[i], 'tokens', -cost)
          end

          return '1:0:ok'
          """,
          String.class);

  private static final DefaultRedisScript<Long> EXTEND_COOLDOWN_SCRIPT =
      new DefaultRedisScript<>(
          """
          local requested = tonumber(ARGV[1])
          local current = redis.call('PTTL', KEYS[1])
          if current == nil or current < 0 then current = 0 end
          if requested > current then
            redis.call('SET', KEYS[1], '1', 'PX', requested)
            return requested
          end
          return current
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final GmailOAuthProperties properties;

  public GmailQuotaReservation reserve(
      String gmailAccount,
      GmailQuotaOperation operation,
      int count,
      GmailQuotaTrafficClass trafficClass) {
    Instant now = Instant.now();
    if (!properties.isQuotaControlEnabled()) {
      return GmailQuotaReservation.allowed(now);
    }

    requireAccount(gmailAccount);
    long unitCost =
        operation == GmailQuotaOperation.GET_MESSAGE
            ? Math.max(1L, properties.getSyncMessageGetQuotaUnits())
            : operation.units();
    long cost = Math.multiplyExact(unitCost, Math.max(1, count));
    Limits limits = limits(cost);

    List<String> keys = new ArrayList<>();
    keys.add(RedisKey.gmailQuotaCooldown(gmailAccount));
    keys.add(RedisKey.gmailQuotaUserTotal(gmailAccount));
    keys.add(RedisKey.gmailQuotaProjectTotal(projectKey()));

    List<String> args = new ArrayList<>();
    args.add(String.valueOf(now.toEpochMilli()));
    args.add(String.valueOf(cost));
    args.add(String.valueOf(Math.max(1L, properties.getQuotaBucketTtl().toMillis())));
    args.add(String.valueOf(limits.userCapacity()));
    args.add(String.valueOf(limits.userRefillPerMs()));
    args.add(String.valueOf(limits.projectCapacity()));
    args.add(String.valueOf(limits.projectRefillPerMs()));

    if (trafficClass == GmailQuotaTrafficClass.BACKGROUND) {
      keys.add(RedisKey.gmailQuotaUserBackground(gmailAccount));
      keys.add(RedisKey.gmailQuotaProjectBackground(projectKey()));
      args.add(String.valueOf(limits.backgroundUserCapacity()));
      args.add(String.valueOf(limits.backgroundUserRefillPerMs()));
      args.add(String.valueOf(limits.backgroundProjectCapacity()));
      args.add(String.valueOf(limits.backgroundProjectRefillPerMs()));
    }

    try {
      String raw = redis.execute(RESERVE_SCRIPT, keys, args.toArray(String[]::new));
      return parseReservation(now, raw);
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Gmail distributed quota reservation failed", exception);
    }
  }

  public Instant penalize(
      String gmailAccount, int attempt, Duration providerRetryAfter) {
    requireAccount(gmailAccount);
    if (!properties.isQuotaControlEnabled()) {
      return Instant.now();
    }

    Duration delay = penaltyDelay(attempt, providerRetryAfter);
    try {
      Long applied =
          redis.execute(
              EXTEND_COOLDOWN_SCRIPT,
              List.of(RedisKey.gmailQuotaCooldown(gmailAccount)),
              String.valueOf(delay.toMillis()));
      long appliedMs = applied == null ? delay.toMillis() : Math.max(delay.toMillis(), applied);
      return Instant.now().plusMillis(appliedMs);
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Gmail provider cooldown update failed", exception);
    }
  }

  Duration penaltyDelay(int attempt, Duration providerRetryAfter) {
    long minimum = Math.max(1_000L, properties.getQuotaCooldownInitial().toMillis());
    long maximum = Math.max(minimum, properties.getQuotaCooldownMax().toMillis());
    int exponent = Math.min(Math.max(0, attempt - 1), 20);

    long exponential;
    try {
      exponential = Math.multiplyExact(minimum, 1L << exponent);
    } catch (ArithmeticException exception) {
      exponential = maximum;
    }

    long providerMs =
        providerRetryAfter == null || providerRetryAfter.isNegative()
            ? 0L
            : providerRetryAfter.toMillis();
    long boundedExponential = Math.min(maximum, exponential);
    return Duration.ofMillis(Math.max(boundedExponential, providerMs));
  }

  private GmailQuotaReservation parseReservation(Instant now, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new RedisOperationException(
          "Gmail quota reservation returned an empty result", null);
    }
    String[] parts = raw.split(":", 3);
    if (parts.length < 2) {
      throw new RedisOperationException(
          "Gmail quota reservation returned a malformed result", null);
    }

    boolean allowed = "1".equals(parts[0]);
    long waitMs;
    try {
      waitMs = Math.max(0L, Long.parseLong(parts[1]));
    } catch (NumberFormatException exception) {
      throw new RedisOperationException("Gmail quota reservation wait time is invalid", exception);
    }

    if (allowed) {
      return GmailQuotaReservation.allowed(now);
    }
    return GmailQuotaReservation.deferred(
        now, Duration.ofMillis(waitMs), parts.length == 3 ? parts[2] : "quota");
  }

  private Limits limits(long cost) {
    int utilization = Math.clamp(properties.getSyncQuotaUtilizationPercent(), 1, 100);
    long userPerMinute = Math.max(1L, properties.getSyncUserQuotaUnitsPerMinute());
    long projectPerMinute = Math.max(1L, properties.getQuotaProjectUnitsPerMinute());

    long usableUser = Math.max(1L, (userPerMinute * utilization) / 100L);
    long usableProject = Math.max(1L, (projectPerMinute * utilization) / 100L);

    if (cost > usableUser || cost > usableProject) {
      throw new IllegalArgumentException(
          "Gmail quota operation cost exceeds the configured per-minute budget");
    }

    int reservePercent = Math.clamp(properties.getQuotaInteractiveReservePercent(), 0, 90);
    long backgroundUser =
        Math.max(1L, (usableUser * (100L - reservePercent)) / 100L);
    long backgroundProject =
        Math.max(1L, (usableProject * (100L - reservePercent)) / 100L);

    double userRefill = usableUser / 60_000.0d;
    double projectRefill = usableProject / 60_000.0d;
    double backgroundUserRefill = backgroundUser / 60_000.0d;
    double backgroundProjectRefill = backgroundProject / 60_000.0d;

    long userCapacity =
        Math.min(usableUser, Math.max(cost, properties.getQuotaUserBurstUnits()));
    long projectCapacity =
        Math.min(usableProject, Math.max(cost, properties.getQuotaProjectBurstUnits()));
    long backgroundUserCapacity =
        Math.min(
            backgroundUser,
            Math.max(cost, properties.getQuotaBackgroundUserBurstUnits()));
    long backgroundProjectCapacity =
        Math.min(
            backgroundProject,
            Math.max(cost, properties.getQuotaBackgroundProjectBurstUnits()));

    return new Limits(
        userCapacity,
        userRefill,
        projectCapacity,
        projectRefill,
        backgroundUserCapacity,
        backgroundUserRefill,
        backgroundProjectCapacity,
        backgroundProjectRefill);
  }

  private String projectKey() {
    return properties.getClientId() == null || properties.getClientId().isBlank()
        ? "default"
        : properties.getClientId();
  }

  private void requireAccount(String gmailAccount) {
    if (gmailAccount == null || gmailAccount.isBlank()) {
      throw new IllegalArgumentException("Gmail account identity is required for quota control");
    }
  }

  private record Limits(
      long userCapacity,
      double userRefillPerMs,
      long projectCapacity,
      double projectRefillPerMs,
      long backgroundUserCapacity,
      double backgroundUserRefillPerMs,
      long backgroundProjectCapacity,
      double backgroundProjectRefillPerMs) {}
}
