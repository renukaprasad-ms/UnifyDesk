package com.messaging.auth.service;

import com.messaging.auth.dto.AuthUserResponse;
import com.messaging.auth.dto.LoginRequest;
import com.messaging.auth.dto.RegisterRequest;
import com.messaging.auth.dto.VerifyEmailRequest;
import com.messaging.common.exception.UnauthorizedException;
import com.messaging.company.service.CompanyService;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.security.jwt.TokenPair;
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
import lombok.RequiredArgsConstructor;
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

  @Transactional
  public AuthUserResponse register(
      RegisterRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    User user =
        userService.createUser(
            new CreateUserRequest(
                request.email(),
                request.name(),
                request.username(),
                request.password(),
                false));
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
    Media media = mediaService.completePreRegisterProfilePictureUpload(
        request.profilePictureMediaId(), request.profilePictureUploadToken(), user.getId());
    userService.attachUserProfilePicture(user.getId(), media);
  }

  @Transactional
  public AuthUserResponse login(
      LoginRequest request, HttpServletRequest servletRequest, HttpHeaders headers) {
    User user = userService.getByEmail(request.email());
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new UnauthorizedException("Invalid email or password");
    }
    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  @Transactional
  public AuthUserResponse refresh(HttpServletRequest servletRequest, HttpHeaders headers) {
    String refreshToken = authCookieService
        .refreshToken(servletRequest)
        .orElseThrow(() -> new UnauthorizedException("Refresh token is required"));
    Claims claims = authTokenService
        .parseRefreshToken(refreshToken)
        .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    UserSession session = userSessionService
        .findActiveByRefreshTokenId(claims.getId())
        .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    User user = session.getUser();
    issueSession(user, servletRequest, headers);
    return toResponse(user);
  }

  @Transactional
  public AuthUserResponse verifyEmail(VerifyEmailRequest request) {
    User user = userService.getUser(currentUserId());
    authOtpService.verifyEmailOtp(user.getEmail(), request.otp());
    user.setVerified(true);
    return toResponse(user);
  }

  public void logout(HttpServletRequest servletRequest, HttpHeaders headers) {
    authCookieService
        .refreshToken(servletRequest)
        .ifPresent(
            refreshToken -> authTokenService
                .parseRefreshToken(refreshToken)
                .ifPresent(claims -> userSessionService.revoke(claims.getId())));
    authTokenService.clearTokenCookies(headers);
  }

  private TokenPair issueSession(
      User user, HttpServletRequest servletRequest, HttpHeaders headers) {
    TokenPair tokenPair = authTokenService.createTokens(
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
        user.getUsername(),
        user.getEmail(),
        user.isVerified(),
        companyCount > 0,
        companyCount);
  }

  private long currentUserId() {
    return Long.parseLong(
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal()
            .toString());
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
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
