package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.credential.CredentialCipher;
import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standalone account security. No admin bypass of mailbox access or existing MFA. */
@Service
public class ConsoleSecurityService {
  private final JdbcTemplate jdbc;
  private final CredentialCipher cipher;
  private final PasswordHasher passwords;
  private final StringRedisTemplate redis;
  private final boolean enrollmentEnabled;
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          """
      local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],300) end; return n
      """,
          Long.class);

  private record Account(
      long id,
      long org,
      String password,
      boolean enabled,
      String secret,
      String pending,
      Instant pendingExpires,
      long lastCounter) {}

  public record Status(boolean mfaEnabled, boolean enrollmentEnabled, int recoveryCodesRemaining) {}

  public record Enrollment(String secret, String authenticatorUri, Instant expiresAt) {}

  public record Reset(String token, Instant expiresAt) {}

  public record Session(String id, Instant expiresAt, boolean current) {}

  public ConsoleSecurityService(
      JdbcTemplate jdbc,
      CredentialCipher cipher,
      PasswordHasher passwords,
      StringRedisTemplate redis,
      @Value("${app.security.console.mfa-enrollment-enabled:false}") boolean enabled) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.passwords = passwords;
    this.redis = redis;
    this.enrollmentEnabled = enabled;
  }

  @Transactional(readOnly = true)
  public Status status(Principal actor) {
    Account account = account(actor.userId(), actor.organizationId(), false);
    Integer remaining =
        jdbc.queryForObject(
            "SELECT count(*) FROM email_console_recovery_codes WHERE user_id=?",
            Integer.class,
            actor.userId());
    return new Status(account.enabled(), enrollmentEnabled, remaining == null ? 0 : remaining);
  }

  @Transactional
  public Enrollment begin(Principal actor, String password) {
    if (!enrollmentEnabled)
      throw new ServiceUnavailableException("Authenticator enrollment is not enabled");
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    requirePassword(account, password);
    if (account.enabled()) throw new ConflictException("Authenticator already enabled");
    String secret = Totp.newSecret();
    Instant expires = Instant.now().plus(Duration.ofMinutes(10));
    jdbc.update(
        "UPDATE email_console_users SET mfa_pending_encrypted=?,mfa_pending_expires_at=? WHERE id=?",
        cipher.encrypt(secret),
        java.sql.Timestamp.from(expires),
        actor.userId());
    String label =
        URLEncoder.encode(
                "UnifyDesk Email:" + actor.organizationId() + ":" + actor.email(),
                StandardCharsets.UTF_8)
            .replace("+", "%20");
    return new Enrollment(
        secret,
        "otpauth://totp/"
            + label
            + "?secret="
            + secret
            + "&issuer=UnifyDesk%20Email&algorithm=SHA1&digits=6&period=30",
        expires);
  }

  @Transactional
  public List<String> confirm(Principal actor, String code) {
    if (!enrollmentEnabled)
      throw new ServiceUnavailableException("Authenticator enrollment is not enabled");
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    if (account.enabled()
        || account.pending() == null
        || account.pendingExpires() == null
        || !account.pendingExpires().isAfter(Instant.now()))
      throw new BadRequestException("Enrollment expired or unavailable");
    long counter =
        Totp.match(cipher.decrypt(account.pending()), code, Instant.now().getEpochSecond(), -1);
    if (counter < 0) throw new BadRequestException("Invalid authenticator code");
    jdbc.update(
        """
        UPDATE email_console_users SET mfa_enabled=TRUE,mfa_secret_encrypted=mfa_pending_encrypted,
          mfa_pending_encrypted=NULL,mfa_pending_expires_at=NULL,mfa_last_counter=? WHERE id=?
        """,
        counter,
        actor.userId());
    List<String> recovery = new ArrayList<>();
    jdbc.update("DELETE FROM email_console_recovery_codes WHERE user_id=?", actor.userId());
    for (int i = 0; i < 10; i++) {
      String token = "ude_recovery_" + ApiKeyCrypto.random(20);
      recovery.add(token);
      jdbc.update(
          "INSERT INTO email_console_recovery_codes(user_id,code_hash) VALUES(?,?)",
          actor.userId(),
          recoveryHash(actor.userId(), token));
    }
    revokeOthers(actor);
    audit(account, "MFA_ENABLED");
    return List.copyOf(recovery);
  }

  @Transactional
  public void disable(Principal actor, String password, String code) {
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    requirePassword(account, password);
    if (!account.enabled()) throw new BadRequestException("Authenticator is not enabled");
    verify(account, code);
    jdbc.update(
        "UPDATE email_console_users SET mfa_enabled=FALSE,mfa_secret_encrypted=NULL,mfa_last_counter=-1 WHERE id=?",
        actor.userId());
    jdbc.update("DELETE FROM email_console_recovery_codes WHERE user_id=?", actor.userId());
    revokeOthers(actor);
    audit(account, "MFA_DISABLED");
  }

  /** Called only after password authentication; locks serialize one-time code consumption. */
  @Transactional
  public void reauthenticate(Principal actor, String password, String code) {
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    requirePassword(account, password);
    if (account.enabled()) verify(account, code);
  }

  /** Called only after password authentication; locks serialize one-time code consumption. */
  @Transactional
  public void verifyLogin(long userId, long organizationId, String code) {
    Account account = account(userId, organizationId, true);
    if (account.enabled()) verify(account, code);
  }

  @Transactional
  public void changePassword(Principal actor, String current, String replacement, String code) {
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    requirePassword(account, current);
    if (account.enabled()) verify(account, code);
    jdbc.update(
        "UPDATE email_console_users SET password_hash=?,updated_at=now() WHERE id=?",
        passwordHash(replacement),
        actor.userId());
    revokeAll(account.id());
    jdbc.update(
        "UPDATE email_console_password_resets SET used_at=now() WHERE user_id=? AND used_at IS NULL",
        actor.userId());
    audit(account, "PASSWORD_CHANGED");
  }

  /** Operator-only issuance: out-of-band verified recovery, never a public token-returning API. */
  @Transactional
  public Reset issueReset(long organizationId, long userId) {
    Account account = account(userId, organizationId, true);
    jdbc.update(
        "UPDATE email_console_password_resets SET used_at=now() WHERE user_id=? AND used_at IS NULL",
        userId);
    String token = "ude_reset_" + ApiKeyCrypto.random(32);
    Instant expires = Instant.now().plus(Duration.ofMinutes(30));
    jdbc.update(
        "INSERT INTO email_console_password_resets(token_hash,organization_id,user_id,expires_at) VALUES(?,?,?,?)",
        ApiKeyCrypto.hash(token),
        organizationId,
        userId,
        java.sql.Timestamp.from(expires));
    audit(account, "PASSWORD_RESET_ISSUED");
    return new Reset(token, expires);
  }

  @Transactional
  public void resetPassword(String token, String replacement, String code, String address) {
    limit("reset-address:" + ApiKeyCrypto.hash(address == null ? "" : address));
    if (token == null || !token.matches("ude_reset_[A-Za-z0-9_-]{40,64}"))
      throw new BadRequestException("Invalid recovery token");
    var matches =
        jdbc.query(
            "SELECT user_id,organization_id FROM email_console_password_resets WHERE token_hash=? AND used_at IS NULL AND expires_at>now()",
            (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)},
            ApiKeyCrypto.hash(token));
    if (matches.isEmpty()) throw new BadRequestException("Invalid recovery token");
    Account account = account(matches.getFirst()[0], matches.getFirst()[1], true);
    // Serialize with issuance and password changes on the same user lock, then recheck.
    int claimed =
        jdbc.update(
            "UPDATE email_console_password_resets SET used_at=now() WHERE token_hash=? AND used_at IS NULL AND expires_at>now()",
            ApiKeyCrypto.hash(token));
    if (claimed != 1) throw new BadRequestException("Invalid recovery token");
    if (account.enabled()) verify(account, code);
    jdbc.update(
        "UPDATE email_console_users SET password_hash=?,updated_at=now() WHERE id=?",
        passwordHash(replacement),
        account.id());
    revokeAll(account.id());
    audit(account, "PASSWORD_RESET_COMPLETED");
  }

  @Transactional(readOnly = true)
  public List<Session> sessions(Principal actor) {
    return jdbc.query(
        "SELECT id,expires_at FROM email_console_sessions WHERE user_id=? AND revoked_at IS NULL AND expires_at>now() ORDER BY created_at DESC LIMIT 100",
        (rs, row) ->
            new Session(
                rs.getString(1),
                rs.getTimestamp(2).toInstant(),
                rs.getLong(1) == actor.sessionId()),
        actor.userId());
  }

  @Transactional
  public void revokeSessions(Principal actor, String password, String code) {
    limit("account:" + actor.userId());
    Account account = account(actor.userId(), actor.organizationId(), true);
    requirePassword(account, password);
    if (account.enabled()) verify(account, code);
    revokeAll(account.id());
    audit(account, "SESSIONS_REVOKED");
  }

  private void verify(Account account, String code) {
    if (code != null && code.startsWith("ude_recovery_") && code.length() <= 80) {
      int used =
          jdbc.update(
              "DELETE FROM email_console_recovery_codes WHERE user_id=? AND code_hash=?",
              account.id(),
              recoveryHash(account.id(), code));
      if (used == 1) {
        audit(account, "RECOVERY_CODE_USED");
        return;
      }
    } else if (account.secret() != null) {
      long counter =
          Totp.match(
              cipher.decrypt(account.secret()),
              code,
              Instant.now().getEpochSecond(),
              account.lastCounter());
      if (counter >= 0) {
        jdbc.update(
            "UPDATE email_console_users SET mfa_last_counter=? WHERE id=?", counter, account.id());
        return;
      }
    }
    throw new BadRequestException("A valid authenticator or unused recovery code is required");
  }

  private Account account(long user, long org, boolean lock) {
    var rows =
        jdbc.query(
            """
        SELECT u.id,u.organization_id,u.password_hash,u.mfa_enabled,u.mfa_secret_encrypted,
          u.mfa_pending_encrypted,u.mfa_pending_expires_at,u.mfa_last_counter
        FROM email_console_users u JOIN email_product_organizations o ON o.id=u.organization_id
        WHERE u.id=? AND u.organization_id=? AND u.status='ACTIVE' AND o.status='ACTIVE'
        """
                + (lock ? " FOR UPDATE OF u" : ""),
            (rs, row) ->
                new Account(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getString(3),
                    rs.getBoolean(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(),
                    rs.getLong(8)),
            user,
            org);
    if (rows.isEmpty()) throw new NotFoundException("Account not found");
    return rows.getFirst();
  }

  private void requirePassword(Account account, String password) {
    if (password == null
        || password.length() > 128
        || !passwords.verify(password, account.password()))
      throw new BadRequestException("Invalid password");
  }

  private String passwordHash(String value) {
    if (value == null || value.length() < 12 || value.length() > 128)
      throw new BadRequestException("Password must be 12 to 128 characters");
    return passwords.hash(value);
  }

  private void revokeAll(long user) {
    jdbc.update(
        "UPDATE email_console_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",
        user);
  }

  private void revokeOthers(Principal actor) {
    jdbc.update(
        "UPDATE email_console_sessions SET revoked_at=now() WHERE user_id=? AND id<>? AND revoked_at IS NULL",
        actor.userId(),
        actor.sessionId());
  }

  private void audit(Account account, String action) {
    jdbc.update(
        "INSERT INTO email_console_security_audit(id,organization_id,user_id,action) VALUES(?,?,?,?)",
        IdGenerator.nextId(),
        account.org(),
        account.id(),
        action);
  }

  private String recoveryHash(long user, String code) {
    return ApiKeyCrypto.hash(user + ":" + code);
  }

  private void limit(String key) {
    try {
      Long count = redis.execute(LIMIT, List.of("ud:email:console:security:" + key));
      if (count == null || count > 10)
        throw new TooManyRequestsException("Too many security attempts");
    } catch (TooManyRequestsException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ServiceUnavailableException("Account security temporarily unavailable");
    }
  }
}
