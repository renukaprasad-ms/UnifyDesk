package com.messaging.auth.service;

import com.messaging.auth.dto.AuthUserResponse;
import com.messaging.auth.dto.LoginRequest;
import com.messaging.auth.dto.LoginResponse;
import com.messaging.auth.dto.RegisterRequest;
import com.messaging.auth.dto.VerifyEmailRequest;
import com.messaging.auth.dto.VerifyTwoFactorRequest;
import com.messaging.common.exception.UnauthorizedException;
import com.messaging.company.service.CompanyService;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.security.jwt.TokenPair;
import com.messaging.security.service.CurrentUserService;
import com.messaging.session.dto.CreateOrUpdateSessionRequest;
import com.messaging.session.entity.SessionPlatform;
import com.messaging.session.entity.UserSession;
import com.messaging.session.service.UserSessionService;
import com.messaging.user.dto.CreateUserRequest;
import com.messaging.user.entity.User;
import com.messaging.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int MAX_IP_LENGTH = 45;
  private static final int MAX_USER_AGENT_LENGTH = 512;

  private final UserService userService;
  private final UserSessionService userSessionService;
  private final AuthTokenService authTokenService;
  private final AuthCookieService authCookieService;
  private final AuthOtpService authOtpService;
  private final PasswordEncoder passwordEncoder;
  private final MediaService mediaService;
  private final CompanyService companyService;
  private final CurrentUserService currentUserService;

  @Value("${app.security.trust-forwarded-headers:false}")
  private boolean trustForwardedHeaders;

  @Transactional
  public AuthUserResponse register(
      RegisterRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    User user =
        userService.createUser(
            new CreateUserRequest(
                request.email(), request.name(), request.username(), request.password(), false));
    attachPreRegisterProfilePicture(user, request);

    issueSession(user, servletRequest, headers);
    authOtpService.issueEmailVerificationOtp(user.getEmail());

    return toResponse(user);
  }

  private void attachPreRegisterProfilePicture(User user, RegisterRequest request) {
    if (request.profilePictureMediaId() == null
        || request.profilePictureMediaId().isBlank()
        || request.profilePictureUploadToken() == null
        || request.profilePictureUploadToken().isBlank()) {
      return;
    }
    Media media =
        mediaService.completePreRegisterProfilePictureUpload(
            request.profilePictureMediaId(), request.profilePictureUploadToken(), user.getId());
    userService.attachUserProfilePicture(user.getId(), media);
  }

  @Transactional
  public LoginResponse login(
      LoginRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    User user = userService.getByEmail(request.email());
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new UnauthorizedException("Invalid email or password");
    }
    if (user.isTwoFactorEnabled()) {
      String challengeId = UUID.randomUUID().toString();
      authOtpService.issueLoginOtp(challengeId, user.getEmail());
      String preAuthToken =
          authTokenService.createPreAuthToken(user.getId().toString(), challengeId);
      return LoginResponse.twoFactorRequired(preAuthToken, 300);
    }

    issueSession(user, servletRequest, headers);
    return LoginResponse.authenticated(toResponse(user));
  }

  @Transactional
  public AuthUserResponse verifyTwoFactor(
      VerifyTwoFactorRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    Claims claims =
        authTokenService
            .parsePreAuthToken(request.preAuthToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired pre-auth token"));
    String challengeId = claims.get("challenge_id", String.class);
    if (challengeId == null || challengeId.isBlank()) {
      throw new UnauthorizedException("Invalid pre-auth token");
    }

    authOtpService.verifyLoginOtp(challengeId, request.otp());
    long userId;
    try {
      userId = Long.parseLong(claims.getSubject());
    } catch (NumberFormatException exception) {
      throw new UnauthorizedException("Invalid pre-auth token");
    }
    User user = userService.getUser(userId);
    if (!user.isTwoFactorEnabled()) {
      throw new UnauthorizedException("Two-factor authentication is not enabled");
    }

    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  @Transactional
  public AuthUserResponse refresh(HttpServletRequest servletRequest, HttpHeaders headers) {
    String refreshToken =
        authCookieService
            .refreshToken(servletRequest)
            .orElseThrow(() -> new UnauthorizedException("Refresh token is required"));
    Claims claims =
        authTokenService
            .parseRefreshToken(refreshToken)
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    UserSession session =
        userSessionService
            .findActiveByRefreshTokenId(claims.getId())
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    User user = session.getUser();
    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  @Transactional
  public AuthUserResponse verifyEmail(VerifyEmailRequest request) {
    User user = userService.getUser(currentUserService.currentUserId());
    authOtpService.verifyEmailOtp(user.getEmail(), request.otp());
    user.setVerified(true);
    return toResponse(user);
  }

  public void logout(HttpServletRequest servletRequest, HttpHeaders headers) {
    authCookieService
        .refreshToken(servletRequest)
        .ifPresent(
            refreshToken ->
                authTokenService
                    .parseRefreshToken(refreshToken)
                    .ifPresent(claims -> userSessionService.revoke(claims.getId())));
    authTokenService.clearTokenCookies(headers);
  }

  private TokenPair issueSession(
      User user, HttpServletRequest servletRequest, HttpHeaders headers) {
    TokenPair tokenPair =
        authTokenService.createTokens(
            user.getId().toString(),
            Map.of(
                "username", user.getUsername(),
                "email", user.getEmail(),
                "verified", user.isVerified()));

    userSessionService.createOrUpdate(
        new CreateOrUpdateSessionRequest(
            user,
            SessionPlatform.WEB,
            authTokenService.refreshTokenId(tokenPair.refreshToken()),
            authTokenService.refreshTokenExpiresAt(tokenPair.refreshToken()),
            clientIp(servletRequest),
            truncate(servletRequest.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT_LENGTH),
            "Web"));
    authTokenService.addTokenCookies(headers, tokenPair);
    return tokenPair;
  }

  private AuthUserResponse toResponse(User user) {
    long companyCount = companyService.companyCountForUser(user.getId());
    return new AuthUserResponse(
        user.getId().toString(),
        user.getName(),
        user.getUsername(),
        user.getEmail(),
        user.getProfilePicture() == null ? null : user.getProfilePicture().getId().toString(),
        user.isVerified(),
        companyCount > 0,
        companyCount);
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (trustForwardedHeaders && forwardedFor != null && !forwardedFor.isBlank()) {
      return truncate(forwardedFor.split(",", 2)[0].trim(), MAX_IP_LENGTH);
    }
    return truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
  }
}
