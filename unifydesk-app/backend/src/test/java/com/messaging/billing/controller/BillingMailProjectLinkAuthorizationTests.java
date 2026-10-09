package com.messaging.billing.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.mailbridge.MailProjectBillingBridgeService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingMailProjectLinkAuthorizationTests {
  @Test
  void rejectsNonSuperAdminsBeforeMappingOrReadingAnyCompanyUsage() {
    var bridge = mock(MailProjectBillingBridgeService.class);
    var authorization = mock(PlatformAdminAuthorizationService.class);
    var user = mock(CurrentUserService.class);
    when(user.currentUserId()).thenReturn(77L);
    doThrow(new ForbiddenException("Denied")).when(authorization).requireSuperAdmin(77L);
    var controller = new BillingMailProjectLinkController(bridge, authorization, user);

    assertThrows(ForbiddenException.class, controller::list);
    assertThrows(ForbiddenException.class, () -> controller.sync("123"));
    assertThrows(ForbiddenException.class, () -> controller.pause("123"));
    assertThrows(ForbiddenException.class, () -> controller.resume("123"));
    assertThrows(ForbiddenException.class, () -> controller.usage(
        Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z")));
    assertThrows(ForbiddenException.class, () -> controller.link(
        new BillingMailProjectLinkController.CreateLinkRequest(
            "1", "123", "approved-onboarding-case-123", true)));
    verifyNoInteractions(bridge);
    verify(authorization, times(6)).requireSuperAdmin(77L);
  }
}
