package com.messaging.emailprovider.product.console;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.emailprovider.common.credential.AesGcmCredentialCipher;
import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.mailbox.MailPostgresFixture;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ConsoleSecurityPostgresTests {
  private static final String PASSWORD = "original-password-1234";
  private static final ConsoleAuthService.Principal OWNER =
      new ConsoleAuthService.Principal(11L, 1L, ConsoleRole.OWNER, "owner@example.test", 101L);

  private ConsoleSecurityService security(MailPostgresFixture db, boolean enabled) {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
    return new ConsoleSecurityService(
        db.jdbc,
        new AesGcmCredentialCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
        new PasswordHasher(),
        redis,
        enabled);
  }

  private void seed(MailPostgresFixture db) {
    db.seed();
    db.jdbc.update(
        "UPDATE email_console_users SET password_hash=? WHERE id=11",
        new PasswordHasher().hash(PASSWORD));
  }

  @Test
  void rfc6238Sha1VectorsAndReplayWindow() {
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    long[] times = {59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000L};
    String[] expected = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
    for (int i = 0; i < times.length; i++)
      assertEquals(expected[i], Totp.code(secret, times[i] / 30, 8));
    String code = Totp.code(secret, 100, 6);
    assertEquals(100, Totp.match(secret, code, 3000, -1));
    assertEquals(-1, Totp.match(secret, code, 3000, 100));
    assertEquals(-1, Totp.match(secret, code, 3150, -1));
    assertTrue(Totp.newSecret().matches("[A-Z2-7]{32}"));
  }

  @Test
  void enrollmentEncryptsSecretAndSerializesOneTimeRecoveryCodes() throws Exception {
    try (var db = new MailPostgresFixture()) {
      seed(db);
      var security = security(db, true);
      var enrollment = db.tx.execute(ignored -> security.begin(OWNER, PASSWORD));
      assertNotNull(enrollment);
      assertNotEquals(
          enrollment.secret(),
          db.jdbc.queryForObject(
              "SELECT mfa_pending_encrypted FROM email_console_users WHERE id=11", String.class));
      String code = Totp.code(enrollment.secret(), Instant.now().getEpochSecond() / 30, 6);
      List<String> recovery = db.tx.execute(ignored -> security.confirm(OWNER, code));
      assertNotNull(recovery);
      assertEquals(10, recovery.size());
      assertTrue(security.status(OWNER).mfaEnabled());
      assertThrows(
          BadRequestException.class,
          () -> db.tx.executeWithoutResult(ignored -> security.verifyLogin(11, 1, code)));
      assertThrows(
          BadRequestException.class,
          () -> db.tx.executeWithoutResult(ignored -> security.verifyLogin(11, 1, null)));
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        Callable<Boolean> attempt =
            () -> {
              try {
                db.tx.executeWithoutResult(
                    ignored -> security.verifyLogin(11, 1, recovery.getFirst()));
                return true;
              } catch (BadRequestException rejected) {
                return false;
              }
            };
        var results = pool.invokeAll(List.of(attempt, attempt));
        assertNotEquals(results.get(0).get(), results.get(1).get());
      }
      assertEquals(9, security.status(OWNER).recoveryCodesRemaining());
      var disabledEnrollment = security(db, false);
      assertThrows(
          BadRequestException.class,
          () -> db.tx.executeWithoutResult(ignored -> disabledEnrollment.verifyLogin(11, 1, null)));
      assertThrows(
          ServiceUnavailableException.class,
          () -> db.tx.executeWithoutResult(ignored -> disabledEnrollment.begin(OWNER, PASSWORD)));
    }
  }

  @Test
  void passwordRecoveryIsTenantBoundSingleUseKeepsMfaAndRevokesAllSessions() throws Exception {
    try (var db = new MailPostgresFixture()) {
      seed(db);
      var security = security(db, true);
      var enrollment = db.tx.execute(ignored -> security.begin(OWNER, PASSWORD));
      var recovery =
          db.tx.execute(
              ignored ->
                  security.confirm(
                      OWNER,
                      Totp.code(enrollment.secret(), Instant.now().getEpochSecond() / 30, 6)));
      assertThrows(
          NotFoundException.class, () -> db.tx.execute(ignored -> security.issueReset(2, 11)));
      var reset = db.tx.execute(ignored -> security.issueReset(1, 11));
      assertThrows(
          BadRequestException.class,
          () ->
              db.tx.executeWithoutResult(
                  ignored ->
                      security.resetPassword(
                          reset.token(), "replacement-password-123", null, "127.0.0.1")));
      db.tx.executeWithoutResult(
          ignored ->
              security.resetPassword(
                  reset.token(), "replacement-password-123", recovery.getFirst(), "127.0.0.1"));
      assertThrows(
          BadRequestException.class,
          () ->
              db.tx.executeWithoutResult(
                  ignored ->
                      security.resetPassword(
                          reset.token(), "another-password-1234", recovery.get(1), "127.0.0.1")));
      assertEquals(
          0L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_console_sessions WHERE user_id=11 AND revoked_at IS NULL",
              Long.class));
      assertTrue(security.status(OWNER).mfaEnabled());
      String hash =
          db.jdbc.queryForObject(
              "SELECT password_hash FROM email_console_users WHERE id=11", String.class);
      assertTrue(new PasswordHasher().verify("replacement-password-123", hash));
      assertFalse(new PasswordHasher().verify(PASSWORD, hash));
      assertEquals(
          1L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_console_security_audit WHERE action='PASSWORD_RESET_COMPLETED'",
              Long.class));
    }
  }
}
