package com.messaging.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RecurringAuthorizationStoreTests {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void existingAuthorizationOrderCannotSwitchPaymentMethods() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending("order_upi", "cust_customer", "PENDING_AUTH");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(55L))).thenReturn(List.of(existing));

    assertThrows(ConflictException.class, () -> store.selectMandateMethod(existing, "card"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  private static final BigDecimal MAX_AMOUNT = new BigDecimal("15000.00");

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void pendingCompanyMandateKeepsProviderOrderAndCustomerOnRetry() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending("order_existing", "cust_existing", "PENDING_AUTH");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of(existing));

    var result = store.prepareCompanyAutopay(10L, MAX_AMOUNT, "INR");

    assertFalse(result.newlyCreated());
    assertEquals("order_existing", result.subscriptionSessionId());
    assertEquals("cust_existing", result.customerId());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void repairsLegacyLocalOnlyPendingMandateBeforeNewProviderSetup() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending("UDS-AP-10-55", null, "PENDING_AUTH");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of(existing));

    var result = store.prepareCompanyAutopay(10L, MAX_AMOUNT, "INR");

    assertTrue(result.newlyCreated());
    assertNull(result.subscriptionSessionId());
    assertNull(result.customerId());
    assertTrue(result.providerSubscriptionId().startsWith("UDS-AP-10-"));
    verify(jdbc)
        .update(
            contains("provider_subscription_session_id = null"),
            startsWith("UDS-AP-10-"),
            eq(MAX_AMOUNT),
            eq("INR"),
            eq(55L));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void refusesToReplayInFlightProviderAuthorization() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending(null, null, "CREATE_PENDING");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of(existing));

    assertThrows(
        ConflictException.class, () -> store.prepareCompanyAutopay(10L, MAX_AMOUNT, "INR"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  private static RecurringAuthorizationStore.Prepared pending(
      String order, String customerId, String status) {
    return new RecurringAuthorizationStore.Prepared(
        55L,
        10L,
        null,
        null,
        "UDS-AP-10-55",
        order,
        "UDA-55",
        null,
        status,
        MAX_AMOUNT,
        "INR",
        false,
        customerId);
  }

  @Test
  void reloadingAnAuthorizationPreservesTheRazorpayCustomer() throws Exception {
    JdbcTemplate jdbc =
        mock(
            JdbcTemplate.class,
            invocation -> {
              if (!invocation.getMethod().getName().equals("query"))
                return RETURNS_DEFAULTS.answer(invocation);
              String sql = invocation.getArgument(0);
              RowMapper<?> mapper = invocation.getArgument(1);
              ResultSet row = mock(ResultSet.class);
              when(row.getLong(anyString())).thenReturn(10L);
              when(row.wasNull()).thenReturn(true);
              when(row.getString("provider_subscription_id")).thenReturn("UDS-AP-10-90");
              when(row.getString("provider_subscription_session_id"))
                  .thenReturn("order_authorization");
              when(row.getString("authorization_status")).thenReturn("PENDING_AUTH");
              when(row.getString("currency")).thenReturn("INR");
              when(row.getString("payment_method_type")).thenReturn("cust_customer");
              when(row.getBigDecimal("max_amount")).thenReturn(new BigDecimal("15000"));
              when(row.findColumn(anyString()))
                  .thenAnswer(
                      column -> {
                        if (column.getArgument(0).equals("payment_method_type")
                            && !sql.contains("payment_method_type")) {
                          throw new SQLException("Column not selected");
                        }
                        return 1;
                      });
              return List.of(mapper.mapRow(row, 0));
            });
    var result =
        new RecurringAuthorizationStore(jdbc)
            .applySubscription(
                10L,
                new PaymentProviderGateway.RecurringAuthorization(
                    "UDS-AP-10-90", "order_authorization", "PENDING_AUTH", "cust_customer"));
    assertThat(result.customerId()).isEqualTo("cust_customer");
    assertThat(result.subscriptionSessionId()).isEqualTo("order_authorization");
  }
}
