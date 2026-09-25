package com.messaging.channel.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppSignupStateService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final MetaWhatsAppProperties properties;

  @Value("${app.redis.key-prefix:messaging}")
  private String keyPrefix;

  public String create(Long companyId, Long userId) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    SignupState payload = new SignupState(companyId, userId, Instant.now());
    try {
      redisTemplate
          .opsForValue()
          .set(
              key(state),
              objectMapper.writeValueAsString(payload),
              properties.getSignupSessionTtl());
      return state;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to create Meta signup session", exception);
    }
  }

  public void consume(String state, Long companyId, Long userId) {
    String raw = redisTemplate.opsForValue().getAndDelete(key(state));
    if (raw == null) {
      throw new BadRequestException("Meta signup session is invalid or expired");
    }
    try {
      SignupState stored = objectMapper.readValue(raw, SignupState.class);
      if (!companyId.equals(stored.companyId()) || !userId.equals(stored.userId())) {
        throw new BadRequestException("Meta signup session does not belong to this user");
      }
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Meta signup session is invalid");
    }
  }

  private String key(String state) {
    return keyPrefix + ":channel:meta-whatsapp:signup:" + state;
  }

  private record SignupState(Long companyId, Long userId, Instant createdAt) {}
}
