package com.messaging.session.repository;

import com.messaging.session.entity.SessionPlatform;
import com.messaging.session.entity.UserSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

  Optional<UserSession> findByUserIdAndPlatform(Long userId, SessionPlatform platform);

  Optional<UserSession> findByRefreshTokenId(String refreshTokenId);
}
