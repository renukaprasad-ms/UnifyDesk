package com.messaging.redis.service;

import com.messaging.redis.exception.RedisOperationException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisCounterService {

  private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT =
      new DefaultRedisScript<>(
          "local current = redis.call('INCR', KEYS[1]) "
              + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
              + "return current",
          Long.class);

  private final StringRedisTemplate stringRedisTemplate;

  public long increment(String key) {
    return run("increment", key, () -> stringRedisTemplate.opsForValue().increment(key));
  }

  public long increment(String key, long amount) {
    return run("increment", key, () -> stringRedisTemplate.opsForValue().increment(key, amount));
  }

  public long decrement(String key) {
    return run("decrement", key, () -> stringRedisTemplate.opsForValue().decrement(key));
  }

  public long get(String key) {
    String value = run("get counter", key, () -> stringRedisTemplate.opsForValue().get(key));
    return value == null ? 0 : Long.parseLong(value);
  }

  public long incrementWithTtl(String key, Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Counter TTL must be positive");
    }
    return run(
        "increment with ttl",
        key,
        () ->
            stringRedisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT, List.of(key), String.valueOf(ttl.toMillis())));
  }

  private <T> T run(String operation, String key, CounterOperation<T> operationCallback) {
    try {
      return operationCallback.execute();
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Redis counter " + operation + " failed", exception);
    }
  }

  @FunctionalInterface
  private interface CounterOperation<T> {
    T execute();
  }
}
