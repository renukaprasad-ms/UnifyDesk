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

  public SignupSession create(Long companyId, Long userId) {
    return create(companyId, userId, SignupMode.CONNECT, null, null);
  }

  public SignupSession createReauthorize(
      Long companyId, Long userId, Long expectedConnectionId, String expectedPhoneNumberId) {
    return create(
        companyId,
        userId,
        SignupMode.REAUTHORIZE,
        expectedConnectionId,
        expectedPhoneNumberId);
  }

  private SignupSession create(
      Long companyId,
      Long userId,
      SignupMode mode,
      Long expectedConnectionId,
      String expectedPhoneNumberId) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.safeSignupSessionTtl());
    SignupState payload =
        new SignupState(
            companyId,
            userId,
            now,
            expiresAt,
            mode,
            expectedConnectionId,
            expectedPhoneNumberId);
    try {
      redisTemplate
          .opsForValue()
          .set(
              key(state),
              objectMapper.writeValueAsString(payload),
              properties.safeSignupSessionTtl());
      return new SignupSession(state, expiresAt);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to create Meta signup session", exception);
    }
  }

  public ConsumedSignupState consume(String state, Long companyId, Long userId) {
    if (state == null || state.isBlank()) {
      throw new BadRequestException("Meta signup session is invalid or expired");
    }

    String raw = redisTemplate.opsForValue().getAndDelete(key(state.trim()));
    if (raw == null) {
      throw new BadRequestException("Meta signup session is invalid, expired, or already used");
    }

    try {
      SignupState stored = objectMapper.readValue(raw, SignupState.class);
      if (stored.expiresAt() == null || !stored.expiresAt().isAfter(Instant.now())) {
        throw new BadRequestException("Meta signup session is invalid or expired");
      }
      if (!companyId.equals(stored.companyId()) || !userId.equals(stored.userId())) {
        throw new BadRequestException("Meta signup session does not belong to this user");
      }
      return new ConsumedSignupState(
          stored.mode() == null ? SignupMode.CONNECT : stored.mode(),
          stored.expectedConnectionId(),
          stored.expectedPhoneNumberId());
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Meta signup session is invalid");
    }
  }

  private String key(String state) {
    return keyPrefix + ":channel:meta-whatsapp:signup:" + state;
  }

  public enum SignupMode {
    CONNECT,
    REAUTHORIZE
  }

  public record SignupSession(String state, Instant expiresAt) {}

  public record ConsumedSignupState(
      SignupMode mode, Long expectedConnectionId, String expectedPhoneNumberId) {}

  private record SignupState(
      Long companyId,
      Long userId,
      Instant createdAt,
      Instant expiresAt,
      SignupMode mode,
      Long expectedConnectionId,
      String expectedPhoneNumberId) {}
}
