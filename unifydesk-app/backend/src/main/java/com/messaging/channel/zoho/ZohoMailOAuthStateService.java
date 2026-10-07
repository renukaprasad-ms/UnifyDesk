package com.messaging.channel.zoho;

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
public class ZohoMailOAuthStateService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ZohoMailOAuthProperties properties;

  @Value("${app.redis.key-prefix:messaging}")
  private String keyPrefix;

  public String create(Long companyId, Long userId, String returnTo) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    OAuthState payload =
        new OAuthState(companyId, userId, Instant.now(), normalizeReturnTo(returnTo));

    try {
      redisTemplate
          .opsForValue()
          .set(key(state), objectMapper.writeValueAsString(payload), properties.getOauthStateTtl());
      return state;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to create Zoho Mail OAuth state", exception);
    }
  }

  public OAuthState consume(String state) {
    if (state == null || state.isBlank()) {
      throw new BadRequestException("Zoho Mail OAuth state is missing");
    }

    String raw = redisTemplate.opsForValue().getAndDelete(key(state));
    if (raw == null) {
      throw new BadRequestException("Zoho Mail OAuth state is invalid or expired");
    }

    try {
      return objectMapper.readValue(raw, OAuthState.class);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Zoho Mail OAuth state is invalid");
    }
  }

  private String key(String state) {
    return keyPrefix + ":channel:zoho-mail:oauth:" + state;
  }

  private String normalizeReturnTo(String returnTo) {
    if ("/inbox/email".equals(returnTo) || "/broadcast/email".equals(returnTo)) {
      return returnTo;
    }
    return "/company/connections";
  }

  public record OAuthState(Long companyId, Long userId, Instant createdAt, String returnTo) {}
}
