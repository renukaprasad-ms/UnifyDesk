package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsConnectSessionService {
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final CredentialCipher cipher;
  private final MetaAdsProperties properties;
  private final MetaAdsOAuthStateService stateService;

  @Value("${app.redis.key-prefix:messaging}")
  private String keyPrefix;

  public String create(Long companyId, Long userId, MetaAdsGraphClient.UserProfile profile, String accessToken, Instant expiresAt) {
    String sessionId = stateService.randomToken();
    ConnectSession session = new ConnectSession(companyId, userId, profile.id(), profile.name(), cipher.encrypt(accessToken), expiresAt);
    try {
      redisTemplate.opsForValue().set(key(sessionId), objectMapper.writeValueAsString(session), properties.getOauthStateTtl());
      return sessionId;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to create Meta Ads connection session", exception);
    }
  }

  public ConnectSession require(String sessionId, Long companyId, Long userId) {
    String raw = redisTemplate.opsForValue().get(key(sessionId));
    if (raw == null) throw new BadRequestException("Meta Ads connection session is invalid or expired");
    try {
      ConnectSession session = objectMapper.readValue(raw, ConnectSession.class);
      if (!companyId.equals(session.companyId()) || !userId.equals(session.userId())) {
        throw new BadRequestException("Meta Ads connection session does not belong to this user");
      }
      return session;
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Meta Ads connection session is invalid");
    }
  }

  public String accessToken(ConnectSession session) { return cipher.decrypt(session.encryptedAccessToken()); }

  public void consume(String sessionId) { redisTemplate.delete(key(sessionId)); }

  private String key(String sessionId) { return keyPrefix + ":ads:meta:connect:" + sessionId; }

  public record ConnectSession(Long companyId, Long userId, String externalUserId, String userName, String encryptedAccessToken, Instant expiresAt) {}
}
