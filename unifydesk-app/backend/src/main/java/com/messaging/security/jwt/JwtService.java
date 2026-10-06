package com.messaging.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

  public static final String SESSION_ID_CLAIM = "session_id";
  private static final String TOKEN_TYPE_CLAIM = "token_type";
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";
  private static final String PRE_AUTH_TOKEN_TYPE = "pre_auth";
  private static final String PASSWORD_RESET_TOKEN_TYPE = "password_reset";
  private static final Duration PRE_AUTH_EXPIRATION = Duration.ofMinutes(5);

  private final JwtProperties properties;
  private PrivateKey privateKey;
  private PublicKey publicKey;

  @PostConstruct
  void initializeKeys() {
    privateKey = parsePrivateKey();
    publicKey = parsePublicKey();
  }

  public TokenPair createTokenPair(String subject) {
    return createTokenPair(subject, Map.of());
  }

  public TokenPair createTokenPair(String subject, Map<String, Object> accessClaims) {
    String refreshToken = createRefreshToken(subject);
    String sessionId = tokenId(refreshToken);
    Map<String, Object> claims = new HashMap<>(accessClaims);
    claims.put(SESSION_ID_CLAIM, sessionId);
    return new TokenPair(createAccessToken(subject, claims), refreshToken);
  }

  public String createAccessToken(String subject) {
    return createAccessToken(subject, Map.of());
  }

  public String createAccessToken(String subject, Map<String, Object> claims) {
    return createToken(subject, ACCESS_TOKEN_TYPE, properties.getAccessExpiration(), claims);
  }

  public String createRefreshToken(String subject) {
    return createToken(subject, REFRESH_TOKEN_TYPE, properties.getRefreshExpiration(), Map.of());
  }

  public String createPreAuthToken(String subject, String challengeId) {
    return createToken(
        subject, PRE_AUTH_TOKEN_TYPE, PRE_AUTH_EXPIRATION, Map.of("challenge_id", challengeId));
  }

  public String createPasswordResetToken(String subject) {
    return createToken(
        subject, PASSWORD_RESET_TOKEN_TYPE, properties.getPasswordResetExpiration(), Map.of());
  }

  public Optional<Claims> parsePreAuthToken(String token) {
    return parseToken(token, PRE_AUTH_TOKEN_TYPE);
  }

  public Optional<Claims> parsePasswordResetToken(String token) {
    return parseToken(token, PASSWORD_RESET_TOKEN_TYPE);
  }

  public boolean isValidAccessToken(String token) {
    return isValidToken(token, ACCESS_TOKEN_TYPE);
  }

  public boolean isValidRefreshToken(String token) {
    return isValidToken(token, REFRESH_TOKEN_TYPE);
  }

  public Optional<Claims> parseAccessToken(String token) {
    return parseToken(token, ACCESS_TOKEN_TYPE);
  }

  public Optional<Claims> parseRefreshToken(String token) {
    return parseToken(token, REFRESH_TOKEN_TYPE);
  }

  public String subject(String token) {
    return claims(token).getSubject();
  }

  public String tokenId(String token) {
    return claims(token).getId();
  }

  public Instant expiresAt(String token) {
    return claims(token).getExpiration().toInstant();
  }

  private Optional<Claims> parseToken(String token, String expectedType) {
    try {
      Claims parsedClaims = claims(token);
      if (!expectedType.equals(parsedClaims.get(TOKEN_TYPE_CLAIM, String.class))) {
        return Optional.empty();
      }
      return Optional.of(parsedClaims);
    } catch (JwtException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private String createToken(
      String subject, String tokenType, Duration expiration, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(expiration);

    return Jwts.builder()
        .claims(claims)
        .id(UUID.randomUUID().toString())
        .issuer(properties.getIssuer())
        .subject(subject)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  private boolean isValidToken(String token, String expectedType) {
    return parseToken(token, expectedType).isPresent();
  }

  private Claims claims(String token) {
    return Jwts.parser()
        .verifyWith(publicKey)
        .requireIssuer(properties.getIssuer())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private PrivateKey parsePrivateKey() {
    try {
      byte[] keyBytes =
          Base64.getDecoder().decode(required(properties.getPrivateKey(), "JWT_PRIVATE_KEY"));
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    } catch (Exception exception) {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY must be a base64 PKCS8 RSA private key", exception);
    }
  }

  private PublicKey parsePublicKey() {
    try {
      byte[] keyBytes =
          Base64.getDecoder().decode(required(properties.getPublicKey(), "JWT_PUBLIC_KEY"));
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    } catch (Exception exception) {
      throw new IllegalStateException(
          "JWT_PUBLIC_KEY must be a base64 X509 RSA public key", exception);
    }
  }

  private String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }
}
