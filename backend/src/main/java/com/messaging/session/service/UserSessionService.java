package com.messaging.session.service;

import com.messaging.session.dto.CreateOrUpdateSessionRequest;
import com.messaging.session.entity.UserSession;
import com.messaging.session.repository.UserSessionRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSessionService {

  private final UserSessionRepository userSessionRepository;

  @Transactional
  public UserSession createOrUpdate(CreateOrUpdateSessionRequest request) {
    Instant now = Instant.now();
    UserSession session =
        userSessionRepository
            .findByUserIdAndPlatform(request.user().getId(), request.platform())
            .orElseGet(
                () -> {
                  UserSession newSession = new UserSession();
                  newSession.setUser(request.user());
                  newSession.setPlatform(request.platform());
                  newSession.setCreatedAt(now);
                  return newSession;
                });

    session.setRefreshTokenId(request.refreshTokenId());
    session.setExpiresAt(request.expiresAt());
    session.setIpAddress(request.ipAddress());
    session.setUserAgent(request.userAgent());
    session.setDeviceName(request.deviceName());
    session.setLastSeenAt(now);
    session.setRevokedAt(null);

    return userSessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  public Optional<UserSession> findActiveByRefreshTokenId(String refreshTokenId) {
    return userSessionRepository.findByRefreshTokenId(refreshTokenId).filter(UserSession::isActive);
  }

  @Transactional
  public void revoke(String refreshTokenId) {
    userSessionRepository
        .findByRefreshTokenId(refreshTokenId)
        .ifPresent(
            session -> {
              session.setRevokedAt(Instant.now());
              userSessionRepository.save(session);
            });
  }
}
