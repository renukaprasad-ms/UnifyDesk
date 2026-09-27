package com.messaging.managedsms.events;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ManagedSmsEventInboxServiceTests {

  @Test
  void exactDuplicateOutsideReplayWindowRemainsIdempotent() {
    Fixture f = fixture("account-a", "req-1");
    when(f.jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
        .thenReturn(1);

    byte[] body =
        payload("abc123", "req-1", "DELIVERED", "2020-01-01T00:00:00+05:30");

    assertThatCode(() -> f.service.receive(body, f.token)).doesNotThrowAnyException();
    verify(f.jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void newOldFutureMissingAndMalformedTimestampsFailClosed() {
    Fixture f = fixture("account-a", "req-1");
    when(f.jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
        .thenReturn(0);

    String old =
        OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
            .minusMinutes(30)
            .toString();
    String future =
        OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
            .plusMinutes(30)
            .toString();

    assertRejected(f, payload("abc123", "req-1", "DELIVERED", old));
    assertRejected(f, payload("abc123", "req-1", "DELIVERED", future));
    assertRejected(
        f,
        "{\"CRQID\":\"abc123\",\"requestId\":\"req-1\",\"status\":\"DELIVERED\"}"
            .getBytes(StandardCharsets.UTF_8));
    assertRejected(f, payload("abc123", "req-1", "DELIVERED", "not-a-time"));
  }

  @Test
  void providerAccountTokenCannotAuthenticateAnotherAccount() {
    ManagedSmsRuntimeProperties properties = properties();
    ManagedSmsWebhookTokenService tokenService = new ManagedSmsWebhookTokenService(properties);
    String accountAToken = tokenService.tokenForAccount("account-a");

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    when(runtime.findCorrelation("abc123"))
        .thenReturn(
            Optional.of(
                new ManagedSmsRuntimeRepository.CorrelationRuntime(
                    "abc123", 10L, 60L, 70L, 1, "req-1", "account-b")));
    ManagedSmsEventInboxService service =
        new ManagedSmsEventInboxService(
            jdbc, new ObjectMapper(), runtime, properties, tokenService);

    assertThatThrownBy(
            () ->
                service.receive(
                    payload(
                        "abc123",
                        "req-1",
                        "DELIVERED",
                        OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toString()),
                    accountAToken))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void freshAccountBoundEventIsAcceptedIntoInbox() {
    Fixture f = fixture("account-a", "req-1");
    when(f.jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
        .thenReturn(0);
    when(f.jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    String now = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toString();
    assertThatCode(
            () ->
                f.service.receive(
                    payload("abc123", "req-1", "DELIVERED", now), f.token))
        .doesNotThrowAnyException();

    verify(f.jdbc).update(anyString(), any(Object[].class));
  }

  private Fixture fixture(String accountKey, String requestId) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsRuntimeProperties properties = properties();
    ManagedSmsWebhookTokenService tokenService = new ManagedSmsWebhookTokenService(properties);
    when(runtime.findCorrelation("abc123"))
        .thenReturn(
            Optional.of(
                new ManagedSmsRuntimeRepository.CorrelationRuntime(
                    "abc123", 10L, 60L, 70L, 1, requestId, accountKey)));
    ManagedSmsEventInboxService service =
        new ManagedSmsEventInboxService(
            jdbc, new ObjectMapper(), runtime, properties, tokenService);
    return new Fixture(jdbc, service, tokenService.tokenForAccount(accountKey));
  }

  private ManagedSmsRuntimeProperties properties() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setWebhookTokenKey("round2-current-token-key-with-high-entropy");
    properties.setWebhookPreviousTokenKey("round2-previous-token-key-with-high-entropy");
    properties.setWebhookMaxBytes(8192);
    properties.setEventReplayWindow(Duration.ofMinutes(10));
    return properties;
  }

  private void assertRejected(Fixture fixture, byte[] body) {
    assertThatThrownBy(() -> fixture.service.receive(body, fixture.token))
        .isInstanceOf(BadRequestException.class);
  }

  private byte[] payload(String correlation, String requestId, String status, String ts) {
    return (
            "{\"CRQID\":\""
                + correlation
                + "\",\"requestId\":\""
                + requestId
                + "\",\"status\":\""
                + status
                + "\",\"ts\":\""
                + ts
                + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private record Fixture(
      JdbcTemplate jdbc, ManagedSmsEventInboxService service, String token) {}
}
