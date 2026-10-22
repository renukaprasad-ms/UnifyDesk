package com.messaging.emailprovider.product.console;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.emailprovider.common.credential.AesGcmCredentialCipher;
import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.mailbox.MailPostgresFixture;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ConsoleRolePostgresTests {
  @Test
  void customRolesAreTenantScopedAuditedAndRevokeAffectedSessions() throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      String password = "role-owner-password-123";
      db.jdbc.update(
          "UPDATE email_console_users SET password_hash=? WHERE id=11",
          new PasswordHasher().hash(password));
      var redis = mock(StringRedisTemplate.class);
      when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
      var security =
          new ConsoleSecurityService(
              db.jdbc,
              new AesGcmCredentialCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
              new PasswordHasher(),
              redis,
              true);
      var roles = new ConsoleRoleService(db.jdbc, security);
      var owner =
          new ConsoleAuthService.Principal(11L, 1L, ConsoleRole.OWNER, "owner@example.test", 101L);
      var admin =
          new ConsoleAuthService.Principal(13L, 1L, ConsoleRole.ADMIN, "admin@example.test", 103L);
      assertThrows(
          ForbiddenException.class,
          () ->
              roles.create(
                  admin, "Escalation", Set.of(ConsolePermission.MANAGE_PROJECTS), password, null));
      var role =
          db.tx.execute(
              ignored ->
                  roles.create(
                      owner,
                      "Domain operators",
                      Set.of(ConsolePermission.MANAGE_DOMAINS),
                      password,
                      null));
      long id = Long.parseLong(role.id());
      assertThrows(
          NotFoundException.class,
          () -> db.tx.executeWithoutResult(ignored -> roles.assign(owner, 21, id, password, null)));
      assertThrows(
          BadRequestException.class,
          () -> db.tx.executeWithoutResult(ignored -> roles.assign(owner, 11, id, password, null)));
      db.tx.executeWithoutResult(ignored -> roles.assign(owner, 12, id, password, null));
      assertEquals(
          Set.of(ConsolePermission.MANAGE_DOMAINS), roles.permissions(1, 12, ConsoleRole.VIEWER));
      assertEquals(Set.of(), roles.permissions(2, 12, ConsoleRole.VIEWER));
      assertEquals(
          0L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_console_sessions WHERE user_id=12 AND revoked_at IS NULL",
              Long.class));
      assertEquals(
          0L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_mailbox_access WHERE user_id=12", Long.class));
      db.tx.executeWithoutResult(
          ignored ->
              roles.update(owner, id, Set.of(ConsolePermission.MANAGE_PROJECTS), password, null));
      assertEquals(
          Set.of(ConsolePermission.MANAGE_PROJECTS), roles.permissions(1, 12, ConsoleRole.VIEWER));
      db.tx.executeWithoutResult(ignored -> roles.assign(owner, 12, null, password, null));
      assertEquals(Set.of(), roles.permissions(1, 12, ConsoleRole.VIEWER));
      assertEquals(
          4L, db.jdbc.queryForObject("SELECT count(*) FROM email_console_role_audit", Long.class));
    }
  }
}
