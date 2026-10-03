package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CashfreeWebhookRecoveryTests {

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

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private String querySql;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      this.querySql = sql;
      return List.of();
    }
  }
}
