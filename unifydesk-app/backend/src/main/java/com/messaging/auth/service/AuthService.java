package com.messaging.auth.service;

import com.messaging.auth.dto.AuthUserResponse;
import com.messaging.auth.dto.CompleteEmailChangeRequest;
import com.messaging.auth.dto.CompletePasswordResetRequest;
import com.messaging.auth.dto.CompleteTwoFactorSettingsRequest;
import com.messaging.auth.dto.EmailChangeChallengeRequest;
import com.messaging.auth.dto.EmailChangeChallengeResponse;
import com.messaging.auth.dto.LoginRequest;
import com.messaging.auth.dto.LoginResponse;
import com.messaging.auth.dto.PasswordResetChallengeResponse;
import com.messaging.auth.dto.PasswordResetRequest;
import com.messaging.auth.dto.RegisterRequest;
import com.messaging.auth.dto.TwoFactorSettingsChallengeRequest;
import com.messaging.auth.dto.TwoFactorSettingsChallengeResponse;
import com.messaging.auth.dto.VerifyEmailRequest;
import com.messaging.auth.dto.VerifyPasswordResetOtpRequest;
import com.messaging.auth.dto.VerifyTwoFactorRequest;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.UnauthorizedException;
import com.messaging.company.service.CompanyService;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.redis.key.RedisKey;
import com.messaging.redis.service.RedisService;
import com.messaging.realtime.service.RealtimeConnectionRegistry;
import com.messaging.security.jwt.TokenPair;
import com.messaging.security.service.CurrentUserService;
import com.messaging.session.dto.CreateOrUpdateSessionRequest;
import com.messaging.session.entity.SessionPlatform;
import com.messaging.session.entity.UserSession;
import com.messaging.session.service.UserSessionService;
import com.messaging.user.dto.CreateUserRequest;
import com.messaging.user.entity.User;
import com.messaging.user.enums.UserStatus;
import com.messaging.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
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
  private static final long PASSWORD_RESET_OTP_EXPIRES_SECONDS = 300;
  private static final long TWO_FACTOR_SETTINGS_EXPIRES_SECONDS = 300;
  private static final long EMAIL_CHANGE_EXPIRES_SECONDS = 300;

  private final UserService userService;
  private final UserSessionService userSessionService;
  private final AuthTokenService authTokenService;
  private final AuthCookieService authCookieService;
  private final AuthOtpService authOtpService;
  private final AuthRateLimitService authRateLimitService;
  private final PasswordEncoder passwordEncoder;
  private final MediaService mediaService;
  private final CompanyService companyService;
  private final CurrentUserService currentUserService;
  private final RedisService redisService;
  private final RealtimeConnectionRegistry realtimeConnectionRegistry;

  @Value("${app.security.trust-forwarded-headers:false}")
  private boolean trustForwardedHeaders;

  @Transactional
  public AuthUserResponse register(
      RegisterRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    String ip = clientIp(servletRequest);
    authRateLimitService.consumeRegistration(ip, request.email());

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
    String ip = clientIp(servletRequest);
    authRateLimitService.consumeLogin(ip, request.identifier());

    User user =
        userService
            .findByIdentifier(request.identifier())
            .orElseThrow(() -> new UnauthorizedException("Invalid username/email or password"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new UnauthorizedException("Invalid username/email or password");
    }
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new UnauthorizedException("Invalid username/email or password");
    }

    authRateLimitService.clearLoginIdentifier(request.identifier());

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
    long userId = parseUserId(claims, "Invalid pre-auth token");
    User user = userService.getUser(userId);
    if (!user.isTwoFactorEnabled()) {
      throw new UnauthorizedException("Two-factor authentication is not enabled");
    }

    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  public PasswordResetChallengeResponse requestPasswordReset(PasswordResetRequest request) {
    authOtpService.enforcePasswordResetRateLimit(request.identifier());
    String challengeId = UUID.randomUUID().toString();
    userService
        .findByIdentifier(request.identifier())
        .ifPresent(
            user ->
                authOtpService.issuePasswordResetOtp(challengeId, user.getEmail(), user.getId()));
    return new PasswordResetChallengeResponse(challengeId, PASSWORD_RESET_OTP_EXPIRES_SECONDS);
  }

  public void verifyPasswordResetOtp(VerifyPasswordResetOtpRequest request, HttpHeaders headers) {
    long userId = authOtpService.verifyPasswordResetOtp(request.challengeId(), request.otp());
    String resetToken = authTokenService.createPasswordResetToken(Long.toString(userId));
    String tokenId = authTokenService.tokenId(resetToken);
    Duration ttl = Duration.between(Instant.now(), authTokenService.tokenExpiresAt(resetToken));
    if (ttl.isZero() || ttl.isNegative()) {
      throw new BadRequestException("Password reset session expired");
    }
    redisService.set(RedisKey.passwordResetGrant(tokenId), "1", ttl);
    authTokenService.addPasswordResetTokenCookie(headers, resetToken);
  }

  @Transactional
  public void completePasswordReset(
      CompletePasswordResetRequest request,
      HttpServletRequest servletRequest,
      HttpHeaders headers) {
    String resetToken =
        authCookieService
            .passwordResetToken(servletRequest)
            .orElseThrow(() -> new BadRequestException("Password reset session expired"));
    Claims claims =
        authTokenService
            .parsePasswordResetToken(resetToken)
            .orElseThrow(() -> new BadRequestException("Password reset session expired"));

    String tokenId = claims.getId();
    if (tokenId == null
        || redisService.getAndDelete(RedisKey.passwordResetGrant(tokenId), String.class) == null) {
      throw new BadRequestException("Password reset session expired");
    }

    long userId = parseUserId(claims, "Password reset session expired");
    userService.resetForgottenPassword(userId, request.password());
    userSessionService.revokeAllForUser(userId);
    realtimeConnectionRegistry.closeForUser(userId);
    authTokenService.clearPasswordResetTokenCookie(headers);
    authTokenService.clearTokenCookies(headers);
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

    UserSession session = userSessionService.findActiveByRefreshTokenId(claims.getId()).orElse(null);
    if (session == null) {
      long userId = parseUserId(claims, "Invalid refresh token");
      userSessionService.revokeAllForUser(userId);
      authTokenService.clearTokenCookies(headers);
      throw new UnauthorizedException("Refresh token reuse detected");
    }

    User user = session.getUser();
    if (user.getStatus() != UserStatus.ACTIVE) {
      userSessionService.revokeAllForUser(user.getId());
      authTokenService.clearTokenCookies(headers);
      throw new UnauthorizedException("Account is not active");
    }

    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  @Transactional
  public AuthUserResponse verifyEmail(VerifyEmailRequest request) {
    User user = userService.getUser(currentUserService.currentUserId());
    if (user.isVerified()) {
      return toResponse(user);
    }
    authOtpService.verifyEmailOtp(user.getEmail(), request.otp());
    user.setVerified(true);
    return toResponse(user);
  }

  public void resendEmailVerification() {
    User user = userService.getUser(currentUserService.currentUserId());
    if (user.isVerified()) {
      throw new BadRequestException("Email is already verified");
    }
    authOtpService.issueEmailVerificationOtp(user.getEmail());
  }

  public EmailChangeChallengeResponse beginEmailChange(EmailChangeChallengeRequest request) {
    long userId = currentUserService.currentUserId();
    User user = userService.getUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new UnauthorizedException("Current password is incorrect");
    }

    String normalizedEmail = userService.validateEmailChangeCandidate(userId, request.newEmail());
    String challengeId = UUID.randomUUID().toString();
    authOtpService.issueEmailChangeOtp(challengeId, normalizedEmail, userId);
    return new EmailChangeChallengeResponse(challengeId, EMAIL_CHANGE_EXPIRES_SECONDS);
  }

  @Transactional
  public void completeEmailChange(CompleteEmailChangeRequest request, HttpHeaders headers) {
    AuthOtpService.EmailChangeVerification verification =
        authOtpService.verifyEmailChangeOtp(request.challengeId(), request.otp());
    long currentUserId = currentUserService.currentUserId();
    if (verification.userId() != currentUserId) {
      throw new UnauthorizedException("Invalid email change challenge");
    }

    userService.completeEmailChange(currentUserId, verification.email());
    userSessionService.revokeAllForUser(currentUserId);
    realtimeConnectionRegistry.closeForUser(currentUserId);
    authTokenService.clearTokenCookies(headers);
  }

  public TwoFactorSettingsChallengeResponse beginTwoFactorSettingsChange(
      TwoFactorSettingsChallengeRequest request) {
    long userId = currentUserService.currentUserId();
    User user = userService.getUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new UnauthorizedException("Current password is incorrect");
    }
    if (user.isTwoFactorEnabled() == request.enabled()) {
      throw new BadRequestException(
          request.enabled()
              ? "Two-factor authentication is already enabled"
              : "Two-factor authentication is already disabled");
    }

    String challengeId = UUID.randomUUID().toString();
    authOtpService.issueTwoFactorSettingsOtp(challengeId, user.getEmail(), request.enabled());
    String challengeToken =
        authTokenService.createTwoFactorSettingsToken(Long.toString(userId), challengeId);
    return new TwoFactorSettingsChallengeResponse(
        challengeToken, TWO_FACTOR_SETTINGS_EXPIRES_SECONDS);
  }

  @Transactional
  public void completeTwoFactorSettingsChange(
      CompleteTwoFactorSettingsRequest request, HttpHeaders headers) {
    Claims claims =
        authTokenService
            .parseTwoFactorSettingsToken(request.challengeToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired security challenge"));
    long userId = parseUserId(claims, "Invalid security challenge");
    if (userId != currentUserService.currentUserId()) {
      throw new UnauthorizedException("Invalid security challenge");
    }
    String challengeId = claims.get("challenge_id", String.class);
    if (challengeId == null || challengeId.isBlank()) {
      throw new UnauthorizedException("Invalid security challenge");
    }

    boolean enabled = authOtpService.verifyTwoFactorSettingsOtp(challengeId, request.otp());
    userService.setTwoFactorEnabled(userId, enabled);
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
    if (user.getStatus() != UserStatus.ACTIVE) {
      userSessionService.revokeAllForUser(user.getId());
      throw new UnauthorizedException("Account is not active");
    }

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
        companyCount,
        userService.appearanceMode(user.getId()));
  }

  private long parseUserId(Claims claims, String message) {
    try {
      return Long.parseLong(claims.getSubject());
    } catch (NumberFormatException exception) {
      throw new UnauthorizedException(message);
    }
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
