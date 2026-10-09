package com.messaging.billing.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.service.GlobalManagedRateService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingAnalyticsSuperAdminTests {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PlatformAdminAuthorizationService permissions =
      mock(PlatformAdminAuthorizationService.class);
  private final CurrentUserService user = mock(CurrentUserService.class);
  private final Instant from = Instant.parse("2026-10-01T00:00:00Z");
  private final Instant to = Instant.parse("2026-10-02T00:00:00Z");

  BillingAnalyticsSuperAdminTests() {
    when(user.currentUserId()).thenReturn(77L);
    doThrow(new ForbiddenException("Forbidden")).when(permissions).requireSuperAdmin(77L);
  }

  @Test
  void financialReadDeniesNonSuperAdminBeforeAnySql() {
    assertThrows(ForbiddenException.class,
        () -> new BillingFinancialAnalyticsController(jdbc, permissions, user).overview(from, to));
    verifyNoInteractions(jdbc);
  }

  @Test
  void managedUsageReadDeniesNonSuperAdminBeforeAnySql() {
    assertThrows(ForbiddenException.class,
        () -> new BillingUsageAnalyticsController(jdbc, permissions, user).managedUsage(from, to));
    verifyNoInteractions(jdbc);
  }

  @Test
  void operationalTrafficReadDeniesNonSuperAdminBeforeAnySql() {
    assertThrows(ForbiddenException.class,
        () -> new BillingChannelTrafficController(jdbc, permissions, user).channelTraffic(from, to));
    verifyNoInteractions(jdbc);
  }

  @Test
  void financialReconciliationDeniesNonSuperAdminBeforeAnySql() {
    assertThrows(ForbiddenException.class,
        () -> new BillingUsageReconciliationController(jdbc, permissions, user).reconcile(from, to));
    verifyNoInteractions(jdbc);
  }

  @Test
  void globalRateReadAndWritesRequireSuperAdmin() {
    GlobalManagedRateService rates = mock(GlobalManagedRateService.class);
    GlobalManagedRateController controller = new GlobalManagedRateController(rates, permissions, user);
    assertThrows(ForbiddenException.class, controller::history);
    assertThrows(ForbiddenException.class,
        () -> controller.revise(null));
    verifyNoInteractions(rates);
    verify(permissions, times(2)).requireSuperAdmin(77L);
  }
}
