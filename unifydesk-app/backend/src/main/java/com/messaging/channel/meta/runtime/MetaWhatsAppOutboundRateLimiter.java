package com.messaging.channel.meta.runtime;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppOutboundRateLimiter {

  static final String KEY_PREFIX = "unifydesk:whatsapp:rate:phone:";

  private static final DefaultRedisScript<String> ACQUIRE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local cooldown = redis.call('PTTL', KEYS[2])
          if cooldown ~= nil and cooldown > 0 then
            return '0:' .. tostring(cooldown) .. ':cooldown'
          end

          local current = redis.call('INCR', KEYS[1])
          if current == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          local ttl = redis.call('PTTL', KEYS[1])
          if current > tonumber(ARGV[2]) then
            return '0:' .. tostring(math.max(0, ttl)) .. ':rate'
          end
          return '1:' .. tostring(math.max(0, ttl)) .. ':ok'
          """,
          String.class);

  private static final DefaultRedisScript<Long> COOLDOWN_SCRIPT =
      new DefaultRedisScript<>(
          """
          local requested = tonumber(ARGV[1])
          local current = redis.call('PTTL', KEYS[1])
          if current == nil or current < requested then
            redis.call('SET', KEYS[1], '1', 'PX', requested)
            return requested
          end
          return current
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final MetaWhatsAppRuntimeProperties properties;

  public void requirePermit(String phoneNumberId) {
    String phoneKey = phoneKey(phoneNumberId);
    int limit = properties.getOutboundMaxPerSecond();
    Duration window = properties.getOutboundRateWindow();
    if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
      throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
    }

    try {
      String raw =
          redisTemplate.execute(
              ACQUIRE_SCRIPT,
              List.of(phoneKey, cooldownKey(phoneKey)),
              Long.toString(window.toMillis()),
              Integer.toString(limit));
      if (!StringUtils.hasText(raw)) {
        throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
      }
      String[] parts = raw.split(":", 3);
      if (parts.length < 1 || !"1".equals(parts[0])) {
        throw new TooManyRequestsException("WHATSAPP_RATE_LIMITED");
      }
    } catch (TooManyRequestsException | ServiceUnavailableException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE", exception);
    }
  }

  public void imposeProviderCooldown(String phoneNumberId, Duration retryAfter) {
    String phoneKey = phoneKey(phoneNumberId);
    Duration effective =
        retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()
            ? properties.getProviderRetryAfterFallback()
            : retryAfter;
    if (effective == null || effective.isZero() || effective.isNegative()) {
      throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
    }
    long millis = Math.max(1L, effective.toMillis());
    try {
      redisTemplate.execute(
          COOLDOWN_SCRIPT, List.of(cooldownKey(phoneKey)), Long.toString(millis));
    } catch (RuntimeException exception) {
      throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE", exception);
    }
  }

  private String phoneKey(String phoneNumberId) {
    if (!StringUtils.hasText(phoneNumberId)) {
      throw new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
    }
    return KEY_PREFIX + phoneNumberId.trim();
  }

  private String cooldownKey(String phoneKey) {
    return phoneKey + ":cooldown";
  }
}
