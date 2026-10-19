package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RecurringAuthorizationStoreTests {

  private static final BigDecimal MAX_AMOUNT = new BigDecimal("15000.00");

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void pendingCompanyMandateKeepsProviderOrderAndCustomerOnRetry() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending("order_existing", "cust_existing", "PENDING_AUTH");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L)))
        .thenReturn(List.of(existing));

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
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L)))
        .thenReturn(List.of(existing));

    var result = store.prepareCompanyAutopay(10L, MAX_AMOUNT, "INR");

    assertTrue(result.newlyCreated());
    assertNull(result.subscriptionSessionId());
    assertNull(result.customerId());
    assertTrue(result.providerSubscriptionId().startsWith("UDS-AP-10-"));
    verify(jdbc).update(
        contains("provider_subscription_session_id = null"),
        startsWith("UDS-AP-10-"), eq(MAX_AMOUNT), eq("INR"), eq(55L));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void refusesToReplayInFlightProviderAuthorization() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var store = new RecurringAuthorizationStore(jdbc);
    var existing = pending(null, null, "CREATE_PENDING");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(10L)))
        .thenReturn(List.of(existing));

    assertThrows(ConflictException.class,
        () -> store.prepareCompanyAutopay(10L, MAX_AMOUNT, "INR"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  private static RecurringAuthorizationStore.Prepared pending(
      String order, String customerId, String status) {
    return new RecurringAuthorizationStore.Prepared(
        55L, 10L, null, null, "UDS-AP-10-55", order,
        "UDA-55", null, status, MAX_AMOUNT, "INR", false, customerId);
  }
}
