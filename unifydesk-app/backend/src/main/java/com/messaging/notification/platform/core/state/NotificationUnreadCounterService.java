package com.messaging.notification.platform.core.state;

import com.messaging.common.id.IdGenerator;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationUnreadCounterService {

  private final JdbcTemplate jdbcTemplate;

  public void increment(long userId, Long companyId, String requiredPermissionKey) {
    adjust(userId, companyId, requiredPermissionKey, 1);
  }

  public void decrement(long userId, Long companyId, String requiredPermissionKey) {
    adjust(userId, companyId, requiredPermissionKey, -1);
  }

  public void adjust(
      long userId, Long companyId, String requiredPermissionKey, long delta) {
    if (delta == 0) {
      return;
    }
    if (companyId != null && requiredPermissionKey != null) {
      mutatePermission(userId, companyId, requiredPermissionKey, delta);
      return;
    }
    mutateBase(userId, companyId, delta);
  }

  public Counts counts(long userId, Long companyId, Set<String> effectivePermissions) {
    long account =
        value(
            "select unread_count from notification_unread_counters where user_id=? and scope_kind='ACCOUNT'",
            userId);
    if (companyId == null) {
      return new Counts(account, 0, account);
    }

    long companyBase =
        value(
            "select unread_count from notification_unread_counters where user_id=? and scope_kind='COMPANY' and company_id=?",
            userId,
            companyId);
    long permissionScoped =
        effectivePermissions == null || effectivePermissions.isEmpty()
            ? 0
            : jdbcTemplate.query(
                """
                select permission_key, unread_count
                from notification_unread_permission_counters
                where user_id=? and company_id=?
                """,
                rs -> {
                  long total = 0;
                  while (rs.next()) {
                    if (effectivePermissions.contains(rs.getString("permission_key"))) {
                      total = Math.addExact(total, rs.getLong("unread_count"));
                    }
                  }
                  return total;
                },
                userId,
                companyId);
    long company = Math.addExact(companyBase, permissionScoped);
    return new Counts(account, company, Math.addExact(account, company));
  }

  private void mutateBase(long userId, Long companyId, long delta) {
    Instant now = Instant.now();
    if (companyId == null) {
      jdbcTemplate.update(
          """
          insert into notification_unread_counters(id,user_id,scope_kind,company_id,unread_count,updated_at)
          values (?,?,'ACCOUNT',null,?,?)
          on conflict (user_id) where scope_kind='ACCOUNT'
          do update set unread_count=greatest(notification_unread_counters.unread_count + ?,0),
                        updated_at=excluded.updated_at
          """,
          IdGenerator.nextId(),
          userId,
          Math.max(0, delta),
          now,
          delta);
    } else {
      jdbcTemplate.update(
          """
          insert into notification_unread_counters(id,user_id,scope_kind,company_id,unread_count,updated_at)
          values (?,?,'COMPANY',?,?,?)
          on conflict (user_id,company_id) where scope_kind='COMPANY'
          do update set unread_count=greatest(notification_unread_counters.unread_count + ?,0),
                        updated_at=excluded.updated_at
          """,
          IdGenerator.nextId(),
          userId,
          companyId,
          Math.max(0, delta),
          now,
          delta);
    }
  }

  private void mutatePermission(
      long userId, long companyId, String requiredPermissionKey, long delta) {
    jdbcTemplate.update(
        """
        insert into notification_unread_permission_counters(
          user_id,company_id,permission_key,unread_count,updated_at)
        values (?,?,?,?,?)
        on conflict (user_id,company_id,permission_key)
        do update set unread_count=greatest(
                        notification_unread_permission_counters.unread_count + ?,0),
                      updated_at=excluded.updated_at
        """,
        userId,
        companyId,
        requiredPermissionKey,
        Math.max(0, delta),
        Instant.now(),
        delta);
  }

  private long value(String sql, Object... args) {
    Long value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : 0L, args);
    return value == null ? 0L : value;
  }

  public record Counts(long account, long company, long total) {}
}
