package com.messaging.auth.service;

import com.messaging.security.jwt.TokenPair;
import com.messaging.security.web.CookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

  private final CookieProperties properties;

  public String accessTokenCookie(String token) {
    return tokenCookie(properties.getAccessName(), token, properties.getAccessMaxAge());
  }

  public String refreshTokenCookie(String token) {
    return tokenCookie(properties.getRefreshName(), token, properties.getRefreshMaxAge());
  }

  public String clearAccessTokenCookie() {
    return tokenCookie(properties.getAccessName(), "", Duration.ZERO);
  }

  public String clearRefreshTokenCookie() {
    return tokenCookie(properties.getRefreshName(), "", Duration.ZERO);
  }

  public void addTokenCookies(HttpHeaders headers, TokenPair tokenPair) {
    addAccessTokenCookie(headers, tokenPair.accessToken());
    addRefreshTokenCookie(headers, tokenPair.refreshToken());
  }

  public void addAccessTokenCookie(HttpHeaders headers, String token) {
    headers.add(HttpHeaders.SET_COOKIE, accessTokenCookie(token));
  }

  public void addRefreshTokenCookie(HttpHeaders headers, String token) {
    headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie(token));
  }

  public void clearTokenCookies(HttpHeaders headers) {
    headers.add(HttpHeaders.SET_COOKIE, clearAccessTokenCookie());
    headers.add(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie());
  }

  public Optional<String> accessToken(HttpServletRequest request) {
    return cookieValue(request, properties.getAccessName());
  }

  public Optional<String> refreshToken(HttpServletRequest request) {
    return cookieValue(request, properties.getRefreshName());
  }

  private Optional<String> cookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }

    return Arrays.stream(cookies)
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  private String tokenCookie(String name, String value, Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(name, value)
            .httpOnly(properties.isHttpOnly())
            .secure(properties.isSecure())
            .path(properties.getPath())
            .maxAge(maxAge)
            .sameSite(properties.getSameSite());

    if (properties.getDomain() != null && !properties.getDomain().isBlank()) {
      builder.domain(properties.getDomain());
    }

    return builder.build().toString();
  }
}
