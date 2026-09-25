package com.messaging.session.dto;

import com.messaging.session.entity.SessionPlatform;
import com.messaging.user.entity.User;
import java.time.Instant;

public record CreateOrUpdateSessionRequest(
    User user,
    SessionPlatform platform,
    String refreshTokenId,
    Instant expiresAt,
    String ipAddress,
    String userAgent,
    String deviceName) {}
