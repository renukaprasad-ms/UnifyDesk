package com.messaging.auth.service;

import com.messaging.security.jwt.JwtService;
import com.messaging.security.jwt.TokenPair;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

  private final JwtService jwtService;
  private final AuthCookieService authCookieService;

  public TokenPair createTokens(String userId) {
    return jwtService.createTokenPair(userId);
  }

  public TokenPair createTokens(String userId, Map<String, Object> accessClaims) {
    return jwtService.createTokenPair(userId, accessClaims);
  }

  public String createPreAuthToken(String userId, String challengeId) {
    return jwtService.createPreAuthToken(userId, challengeId);
  }

  public Optional<Claims> parsePreAuthToken(String token) {
    return jwtService.parsePreAuthToken(token);
  }

  public String createPasswordResetToken(String userId) {
    return jwtService.createPasswordResetToken(userId);
  }

  public Optional<Claims> parsePasswordResetToken(String token) {
    return jwtService.parsePasswordResetToken(token);
  }

  public String createTwoFactorSettingsToken(String userId, String challengeId) {
    return jwtService.createTwoFactorSettingsToken(userId, challengeId);
  }

  public Optional<Claims> parseTwoFactorSettingsToken(String token) {
    return jwtService.parseTwoFactorSettingsToken(token);
  }

  public String tokenId(String token) {
    return jwtService.tokenId(token);
  }

  public Instant tokenExpiresAt(String token) {
    return jwtService.expiresAt(token);
  }

  public void addPasswordResetTokenCookie(HttpHeaders headers, String token) {
    authCookieService.addPasswordResetTokenCookie(headers, token);
  }

  public void clearPasswordResetTokenCookie(HttpHeaders headers) {
    authCookieService.clearPasswordResetTokenCookie(headers);
  }

  public void addTokenCookies(HttpHeaders headers, TokenPair tokenPair) {
    authCookieService.addTokenCookies(headers, tokenPair);
  }

  public String refreshTokenId(String refreshToken) {
    return jwtService.tokenId(refreshToken);
  }

  public Instant refreshTokenExpiresAt(String refreshToken) {
    return jwtService.expiresAt(refreshToken);
  }

  public Optional<Claims> parseRefreshToken(String refreshToken) {
    return jwtService.parseRefreshToken(refreshToken);
  }

  public void clearTokenCookies(HttpHeaders headers) {
    authCookieService.clearTokenCookies(headers);
  }
}
