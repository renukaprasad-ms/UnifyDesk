package com.messaging.contacts.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RecipientActionTokenServiceTests {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Test
  void tokenRoundTripsExactTenantContactAndIdentity() {
    RecipientActionTokenService service = service(NOW, key((byte) 1), Duration.ofDays(30));

    var action = service.validate(service.issue(101L, 201L, 301L));

    assertThat(action.companyId()).isEqualTo(101L);
    assertThat(action.contactId()).isEqualTo(201L);
    assertThat(action.identityId()).isEqualTo(301L);
    assertThat(action.actionId()).isNotBlank();
  }

  @Test
  void tokenPayloadIsOpaqueRatherThanPlaintextIds() {
    RecipientActionTokenService service = service(NOW, key((byte) 2), Duration.ofDays(30));
    String token = service.issue(910191L, 820282L, 730373L);

    String packed =
        new String(Base64.getUrlDecoder().decode(token), StandardCharsets.ISO_8859_1);

    assertThat(packed).doesNotContain("910191", "820282", "730373", "EMAIL", "MARKETING");
  }

  @Test
  void tamperedTokenIsRejected() {
    RecipientActionTokenService service = service(NOW, key((byte) 3), Duration.ofDays(30));
    String token = service.issue(1L, 2L, 3L);
    int index = token.length() / 2;
    char replacement = token.charAt(index) == 'A' ? 'B' : 'A';
    String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

    assertThatThrownBy(() -> service.validate(tampered))
        .isInstanceOf(InvalidRecipientActionException.class);
  }

  @Test
  void tokenCannotBeValidatedWithAnotherKey() {
    String token = service(NOW, key((byte) 4), Duration.ofDays(30)).issue(1L, 2L, 3L);

    assertThatThrownBy(
            () -> service(NOW, key((byte) 5), Duration.ofDays(30)).validate(token))
        .isInstanceOf(InvalidRecipientActionException.class);
  }

  @Test
  void expiredTokenIsRejected() {
    String token = service(NOW, key((byte) 6), Duration.ofHours(1)).issue(1L, 2L, 3L);

    assertThatThrownBy(
            () ->
                service(NOW.plus(Duration.ofHours(2)), key((byte) 6), Duration.ofHours(1))
                    .validate(token))
        .isInstanceOf(InvalidRecipientActionException.class);
  }

  @Test
  void missingKeyFailsClosedInsteadOfIssuingInsecureToken() {
    RecipientActionProperties properties = properties("", Duration.ofDays(30));

    assertThatThrownBy(
            () ->
                new RecipientActionTokenService(
                        properties,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC))
                    .issue(1L, 2L, 3L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_RECIPIENT_ACTION_KEY");
  }

  @Test
  void malformedKeyFailsClosed() {
    RecipientActionProperties properties =
        properties(Base64.getEncoder().encodeToString(new byte[16]), Duration.ofDays(30));

    assertThatThrownBy(
            () ->
                new RecipientActionTokenService(
                        properties,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC))
                    .issue(1L, 2L, 3L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  private RecipientActionTokenService service(Instant now, String key, Duration ttl) {
    return new RecipientActionTokenService(
        properties(key, ttl), new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
  }

  private RecipientActionProperties properties(String key, Duration ttl) {
    RecipientActionProperties properties = new RecipientActionProperties();
    properties.setEncryptionKey(key);
    properties.setTokenTtl(ttl);
    properties.setClockSkew(Duration.ofSeconds(5));
    return properties;
  }

  private String key(byte value) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, value);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
