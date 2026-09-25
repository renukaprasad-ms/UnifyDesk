package com.messaging.kafka.idempotency;

import com.messaging.kafka.config.KafkaProperties;
import com.messaging.kafka.event.KafkaEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaEventIdempotencyService {

  private final StringRedisTemplate stringRedisTemplate;
  private final KafkaProperties kafkaProperties;

  public boolean isProcessed(KafkaEvent<?> event) {
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(processedKey(event)));
  }

  public void markProcessed(KafkaEvent<?> event) {
    Duration ttl = kafkaProperties.getIdempotency().getTtl();
    stringRedisTemplate.opsForValue().set(processedKey(event), "1", ttl);
  }

  private String processedKey(KafkaEvent<?> event) {
    return "messaging:kafka:processed:" + event.eventType() + ":" + event.eventId();
  }
}
