package com.messaging.contacts.consent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipientActionTokenService {

  private static final int TOKEN_VERSION = 1;
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final byte[] AAD =
      "unifydesk:recipient-action:email-unsubscribe:v1".getBytes(StandardCharsets.UTF_8);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RecipientActionProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public RecipientActionTokenService(
      RecipientActionProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, Clock.systemUTC());
  }

  RecipientActionTokenService(
      RecipientActionProperties properties, ObjectMapper objectMapper, Clock clock) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(long companyId, long contactId, long identityId) {
    if (companyId <= 0 || contactId <= 0 || identityId <= 0) {
      throw new IllegalArgumentException("Recipient action subject is invalid");
    }
    Instant issuedAt = clock.instant();
    Duration ttl = requirePositive(properties.getTokenTtl(), "token TTL");
    TokenPayload payload =
        new TokenPayload(
            TOKEN_VERSION,
            companyId,
            contactId,
            identityId,
            Channel.EMAIL.name(),
            Purpose.MARKETING.name(),
            issuedAt.getEpochSecond(),
            issuedAt.plus(ttl).getEpochSecond(),
            randomNonce());
    try {
      byte[] iv = new byte[IV_LENGTH];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      cipher.updateAAD(AAD);
      byte[] plaintext = objectMapper.writeValueAsBytes(payload);
      byte[] encrypted = cipher.doFinal(plaintext);
      byte[] packed =
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
    } catch (GeneralSecurityException | java.io.IOException exception) {
      throw new IllegalStateException("Unable to issue recipient action token", exception);
    }
  }

  public ValidatedRecipientAction validate(String token) {
    if (token == null || token.isBlank() || token.length() > 4096) {
      throw new InvalidRecipientActionException();
    }
    try {
      byte[] packed = Base64.getUrlDecoder().decode(token);
      if (packed.length <= IV_LENGTH + 16) throw new InvalidRecipientActionException();
      byte[] iv = new byte[IV_LENGTH];
      byte[] encrypted = new byte[packed.length - IV_LENGTH];
      System.arraycopy(packed, 0, iv, 0, IV_LENGTH);
      System.arraycopy(packed, IV_LENGTH, encrypted, 0, encrypted.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      cipher.updateAAD(AAD);
      TokenPayload payload = objectMapper.readValue(cipher.doFinal(encrypted), TokenPayload.class);
      validatePayload(payload);
      return new ValidatedRecipientAction(
          payload.companyId(),
          payload.contactId(),
          payload.identityId(),
          actionId(payload.nonce()));
    } catch (InvalidRecipientActionException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidRecipientActionException(exception);
    }
  }

  private void validatePayload(TokenPayload payload) {
    if (payload == null
        || payload.version() != TOKEN_VERSION
        || payload.companyId() <= 0
        || payload.contactId() <= 0
        || payload.identityId() <= 0
        || !Channel.EMAIL.name().equals(payload.channel())
        || !Purpose.MARKETING.name().equals(payload.purpose())
        || payload.nonce() == null
        || payload.nonce().isBlank()) {
      throw new InvalidRecipientActionException();
    }

    Instant issuedAt = Instant.ofEpochSecond(payload.issuedAtEpochSecond());
    Instant expiresAt = Instant.ofEpochSecond(payload.expiresAtEpochSecond());
    Instant now = clock.instant();
    Duration skew = requireNonNegative(properties.getClockSkew(), "clock skew");
    Duration ttl = requirePositive(properties.getTokenTtl(), "token TTL");
    if (issuedAt.isAfter(now.plus(skew))
        || !expiresAt.isAfter(now)
        || expiresAt.isBefore(issuedAt)
        || Duration.between(issuedAt, expiresAt).compareTo(ttl.plus(skew)) > 0) {
      throw new InvalidRecipientActionException();
    }
  }

  private SecretKeySpec key() {
    String configured = properties.getEncryptionKey();
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "APP_RECIPIENT_ACTION_KEY must be configured before issuing recipient actions");
    }
    final byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configured.trim());
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "APP_RECIPIENT_ACTION_KEY must be Base64 encoded", exception);
    }
    if (decoded.length != 32) {
      throw new IllegalStateException(
          "APP_RECIPIENT_ACTION_KEY must decode to exactly 32 bytes for AES-256-GCM");
    }
    return new SecretKeySpec(decoded, "AES");
  }

  private String randomNonce() {
    byte[] nonce = new byte[16];
    SECURE_RANDOM.nextBytes(nonce);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
  }

  private String actionId(String nonce) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private Duration requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException("Recipient action " + name + " must be positive");
    }
    return value;
  }

  private Duration requireNonNegative(Duration value, String name) {
    if (value == null || value.isNegative()) {
      throw new IllegalStateException("Recipient action " + name + " must not be negative");
    }
    return value;
  }

  private record TokenPayload(
      int version,
      long companyId,
      long contactId,
      long identityId,
      String channel,
      String purpose,
      long issuedAtEpochSecond,
      long expiresAtEpochSecond,
      String nonce) {}

  public record ValidatedRecipientAction(
      long companyId, long contactId, long identityId, String actionId) {}
}
