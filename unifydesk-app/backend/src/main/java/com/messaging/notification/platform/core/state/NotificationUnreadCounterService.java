package com.messaging.notification.platform.core.state;

import com.messaging.common.id.IdGenerator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationUnreadCounterService {

  private final JdbcTemplate jdbcTemplate;

  public void increment(long userId, Long companyId) {
    mutate(userId, companyId, 1);
  }

  public void decrement(long userId, Long companyId) {
    mutate(userId, companyId, -1);
  }

  public Counts counts(long userId, Long companyId) {
    long account =
        value(
            "select unread_count from notification_unread_counters where user_id=? and scope_kind='ACCOUNT'",
            userId);
    long company =
        companyId == null
            ? 0
            : value(
                "select unread_count from notification_unread_counters where user_id=? and scope_kind='COMPANY' and company_id=?",
                userId,
                companyId);
    return new Counts(account, company, account + company);
  }

  private void mutate(long userId, Long companyId, long delta) {
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

  private long value(String sql, Object... args) {
    Long value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : 0L, args);
    return value == null ? 0L : value;
  }

  public record Counts(long account, long company, long total) {}
}
