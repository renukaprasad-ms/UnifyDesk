package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastRateLimiter {

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
          local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
          local tokens = tonumber(data[1])
          local ts = tonumber(data[2])
          local now = tonumber(ARGV[1])
          local rate = tonumber(ARGV[2])
          local capacity = tonumber(ARGV[3])
          if tokens == nil then tokens = capacity end
          if ts == nil then ts = now end
          local elapsed = math.max(0, now - ts)
          tokens = math.min(capacity, tokens + (elapsed * rate / 1000.0))
          local allowed = 0
          if tokens >= 1 then
            tokens = tokens - 1
            allowed = 1
          end
          redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
          redis.call('PEXPIRE', KEYS[1], 60000)
          return allowed
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final BroadcastProperties properties;

  public boolean tryAcquire(BroadcastSenderConnection connection) {
    int rate = effectiveRate(connection);
    if (rate <= 0) return false;
    String key =
        "messaging:broadcast:rate:"
            + connection.getProvider().name()
            + ":"
            + connection.getId();
    Long allowed =
        redisTemplate.execute(
            SCRIPT,
            List.of(key),
            Long.toString(System.currentTimeMillis()),
            Integer.toString(rate),
            Integer.toString(rate));
    return Long.valueOf(1L).equals(allowed);
  }

  int effectiveRate(BroadcastSenderConnection connection) {
    double providerRate =
        connection.getMaxSendRate() == null ? 1.0 : Math.max(1.0, connection.getMaxSendRate());
    int providerFloor = Math.max(1, (int) Math.floor(providerRate));
    return Math.min(providerFloor, Math.max(1, properties.getSesMaxPerSecondCap()));
  }
}
