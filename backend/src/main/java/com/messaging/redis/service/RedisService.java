package com.messaging.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.redis.exception.RedisOperationException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisService {

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;

  public void set(String key, Object value, Duration ttl) {
    requireTtl(ttl);
    if (value == null) {
      delete(key);
      return;
    }
    run(
        "set",
        key,
        () -> {
          redisTemplate.opsForValue().set(key, value, ttl);
          return null;
        });
  }

  public <T> T get(String key, Class<T> type) {
    return run("get", key, () -> objectMapper.convertValue(redisTemplate.opsForValue().get(key), type));
  }

  public boolean delete(String key) {
    return Boolean.TRUE.equals(run("delete", key, () -> redisTemplate.delete(key)));
  }

  public boolean exists(String key) {
    return Boolean.TRUE.equals(run("exists", key, () -> redisTemplate.hasKey(key)));
  }

  public boolean expire(String key, Duration ttl) {
    requireTtl(ttl);
    return Boolean.TRUE.equals(run("expire", key, () -> redisTemplate.expire(key, ttl)));
  }

  public Duration getTtl(String key) {
    Long seconds = run("ttl", key, () -> redisTemplate.getExpire(key));
    return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
  }

  private void requireTtl(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Temporary Redis values require a positive TTL");
    }
  }

  private <T> T run(String operation, String key, RedisOperation<T> operationCallback) {
    try {
      return operationCallback.execute();
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Redis " + operation + " failed for key " + namespace(key), exception);
    }
  }

  private String namespace(String key) {
    int index = key == null ? -1 : key.indexOf(':');
    return index > 0 ? key.substring(0, index) : "unknown";
  }

  @FunctionalInterface
  private interface RedisOperation<T> {
    T execute();
  }
}
