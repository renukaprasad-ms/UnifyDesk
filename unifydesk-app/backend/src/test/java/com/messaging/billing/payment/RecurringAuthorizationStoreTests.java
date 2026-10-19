package com.messaging.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RecurringAuthorizationStoreTests {
  @Test
  void reloadingAnAuthorizationPreservesTheRazorpayCustomer() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
      if (!invocation.getMethod().getName().equals("query")) return RETURNS_DEFAULTS.answer(invocation);
      String sql = invocation.getArgument(0);
      RowMapper<?> mapper = invocation.getArgument(1);
      ResultSet row = mock(ResultSet.class);
      when(row.getLong(anyString())).thenReturn(10L);
      when(row.wasNull()).thenReturn(true);
      when(row.getString("provider_subscription_id")).thenReturn("UDS-AP-10-90");
      when(row.getString("provider_subscription_session_id")).thenReturn("order_authorization");
      when(row.getString("authorization_status")).thenReturn("PENDING_AUTH");
      when(row.getString("currency")).thenReturn("INR");
      when(row.getString("payment_method_type")).thenReturn("cust_customer");
      when(row.getBigDecimal("max_amount")).thenReturn(new BigDecimal("15000"));
      when(row.findColumn(anyString())).thenAnswer(column -> {
        if (column.getArgument(0).equals("payment_method_type") && !sql.contains("payment_method_type")) {
          throw new SQLException("Column not selected");
        }
        return 1;
      });
      return List.of(mapper.mapRow(row, 0));
    });
    var result = new RecurringAuthorizationStore(jdbc).applySubscription(10L,
        new PaymentProviderGateway.RecurringAuthorization(
            "UDS-AP-10-90", "order_authorization", "PENDING_AUTH", "cust_customer"));
    assertThat(result.customerId()).isEqualTo("cust_customer");
    assertThat(result.subscriptionSessionId()).isEqualTo("order_authorization");
  }
}
