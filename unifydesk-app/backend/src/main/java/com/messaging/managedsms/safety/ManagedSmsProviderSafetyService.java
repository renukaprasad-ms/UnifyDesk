package com.messaging.managedsms.safety;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsProviderSafetyService {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedSmsRuntimeProperties properties;

  @Transactional(readOnly = true)
  public boolean isAvailable(String providerAccountKey) {
    if (providerAccountKey == null || providerAccountKey.isBlank()) return false;
    BigDecimal reserve = properties.getProviderBalanceReserve();
    if (reserve == null || reserve.signum() <= 0) {
      return true;
    }
    List<Snapshot> rows =
        jdbcTemplate.query(
            """
            select balance, sending_enabled, valid_until
              from managed_sms_provider_snapshots
             where provider_account_key = ?
               and valid_until >= now()
             order by observed_at desc
             limit 1
            """,
            (rs, rowNum) ->
                new Snapshot(
                    rs.getBigDecimal("balance"),
                    rs.getBoolean("sending_enabled"),
                    rs.getTimestamp("valid_until").toInstant()),
            providerAccountKey);
    if (rows.isEmpty()) return false;
    Snapshot snapshot = rows.getFirst();
    return snapshot.sendingEnabled()
        && snapshot.balance() != null
        && snapshot.balance().compareTo(reserve) > 0;
  }

  @Transactional
  public void tripInsufficientCredit(String providerAccountKey) {
    if (providerAccountKey == null || providerAccountKey.isBlank()) return;
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into managed_sms_provider_snapshots (
          id, provider_account_key, provider_client_ref, balance, credit_limit,
          max_send_rate, sending_enabled, observed_at, valid_until
        ) values (?, ?, null, 0, null, null, false, ?, ?)
        """,
        com.messaging.common.id.IdGenerator.nextId(),
        providerAccountKey,
        Timestamp.from(now),
        Timestamp.from(now.plus(10, ChronoUnit.MINUTES)));
  }

  private record Snapshot(BigDecimal balance, boolean sendingEnabled, Instant validUntil) {}
}
