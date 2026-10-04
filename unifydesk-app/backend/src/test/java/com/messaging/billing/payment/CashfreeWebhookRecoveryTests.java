package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CashfreeWebhookRecoveryTests {

  @Test
  void exactFreshDuplicateUsesSameDurableInboxKeyAndConflictDedupe() {
    DuplicateInboxJdbcTemplate jdbc = new DuplicateInboxJdbcTemplate();
    CashfreeWebhookService service =
        new CashfreeWebhookService(
            jdbc,
            new ObjectMapper(),
            mock(PaymentConfirmationService.class),
            mock(RefundStore.class),
            mock(RecurringAuthorizationStore.class),
            new CashfreeProperties());

    byte[] raw = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}".getBytes(StandardCharsets.UTF_8);
    service.receiveVerified(raw, Instant.parse("2026-10-04T04:00:00Z"));
    service.receiveVerified(raw, Instant.parse("2026-10-04T04:00:01Z"));

    assertEquals(2, jdbc.eventKeys.size());
    assertEquals(jdbc.eventKeys.get(0), jdbc.eventKeys.get(1));
    assertTrue(jdbc.insertSql.contains("on conflict (provider, provider_event_key) do nothing"));
  }

  @Test
  void retryWorkerClaimsVerifiedReceivedRowsWithSkipLockedAndNeverReclaimsProcessedRows() {
    CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
    CashfreeProperties properties = new CashfreeProperties();
    CashfreeWebhookService service =
        new CashfreeWebhookService(
            jdbc,
            new ObjectMapper(),
            mock(PaymentConfirmationService.class),
            mock(RefundStore.class),
            mock(RecurringAuthorizationStore.class),
            properties);

    service.retryDueEvents();

    String sql = jdbc.querySql;
    assertTrue(sql.contains("signature_verified_at is not null"));
    assertTrue(sql.contains("processing_status = 'RECEIVED'"));
    assertTrue(sql.contains("processing_status = 'RETRY'"));
    assertTrue(sql.contains("processing_status = 'PROCESSING' and claimed_until < now()"));
    assertTrue(sql.contains("for update skip locked"));
    assertTrue(sql.contains("set processing_status='PROCESSING'"));
    assertFalse(sql.contains("processing_status = 'PROCESSED'"));
  }

  private static final class DuplicateInboxJdbcTemplate extends JdbcTemplate {
    private final List<String> eventKeys = new ArrayList<>();
    private String insertSql;
    private int inserts;

    @Override
    public int update(String sql, Object... args) {
      if (sql.contains("insert into payment_provider_events")) {
        insertSql = sql;
        eventKeys.add((String) args[1]);
        return inserts++ == 0 ? 1 : 0;
      }
      return 1;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      return List.of();
    }
  }

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private String querySql;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      this.querySql = sql;
      return List.of();
    }
  }
}
