package com.messaging.billing.mailbridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.company.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class MailProjectBillingBridgeSafetyTests {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CompanyRepository companies = mock(CompanyRepository.class);
  private final MailProductUsageClient mail = mock(MailProductUsageClient.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

  private MailProjectBillingBridgeService service(boolean enabled) {
    return new MailProjectBillingBridgeService(jdbc, companies, mail, manager, enabled, 100);
  }

  @Test
  void disabledBridgeNeverCallsSourceOrDatabase() {
    var bridge = service(false);
    assertThrows(ServiceUnavailableException.class, () -> bridge.sync(123L));
    assertThrows(ServiceUnavailableException.class,
        () -> bridge.link(5L, 123L, 77L, "approved onboarding ticket 123", true));
    bridge.scheduledSync();
    verifyNoInteractions(mail, jdbc, companies, manager);
  }

  @Test
  void cannotLinkWithoutExplicitlyVerifiedOwnership() {
    var bridge = service(true);
    assertThrows(BadRequestException.class,
        () -> bridge.link(5L, 123L, 77L, "approved onboarding ticket 123", false));
    assertThrows(BadRequestException.class,
        () -> bridge.link(5L, 123L, 77L, "short", true));
    verifyNoInteractions(mail, jdbc, companies, manager);
  }

  @Test
  void reportOnlyDoesNotCreateCentralFinancialCharges() {
    assertFalse(MailProjectBillingBridgeService.class.isAnnotationPresent(
        org.springframework.transaction.annotation.Transactional.class));
    // The bridge stores operational source imports only; no UsageBillingService,
    // InvoiceService or subscription-service dependency is accepted by constructor.
    assertEquals(6,
        MailProjectBillingBridgeService.class.getConstructors()[0].getParameterCount());
  }
}
