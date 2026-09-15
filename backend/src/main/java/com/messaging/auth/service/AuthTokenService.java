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
