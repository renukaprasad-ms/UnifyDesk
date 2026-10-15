package com.messaging.managedemail.control.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.CreateSenderRequest;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.CustomerDomainRequest;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.HostedDomainRequest;
import com.messaging.managedemail.control.security.ManagedEmailControlPlaneRateLimiter;
import com.messaging.managedemail.control.service.ManagedEmailDomainService;
import com.messaging.managedemail.control.service.ManagedEmailOverviewService;
import com.messaging.managedemail.control.service.ManagedEmailSenderService;
import com.messaging.security.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagedEmailControllerAuthorizationTests {

  @Mock private ManagedEmailOverviewService overviewService;
  @Mock private ManagedEmailDomainService domainService;
  @Mock private ManagedEmailSenderService senderService;
  @Mock private ManagedEmailControlPlaneRateLimiter rateLimiter;
  @Mock private CompanyAuthorizationService authorizationService;
  @Mock private CurrentUserService currentUserService;

  private ManagedEmailController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ManagedEmailController(
            overviewService,
            domainService,
            senderService,
            rateLimiter,
            authorizationService,
            currentUserService);
    when(currentUserService.currentUserId()).thenReturn(7L);
    doThrow(new SecurityException("denied"))
        .when(authorizationService)
        .requirePermission(eq(7L), eq(99L), anyString());
  }

  @Test
  void everyEndpointAuthorizesBeforeReadingOrMutatingCompanyResources() {
    assertThatThrownBy(() -> controller.overview(99L)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.domains(99L)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(
            () ->
                controller.createCustomerDomain(
                    99L, new CustomerDomainRequest("example.com", "customer-key")))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(
            () -> controller.createHostedDomain(99L, new HostedDomainRequest("hosted-key", "acme")))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.checkSubdomain(99L, "acme"))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.domain(99L, 1L)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.refreshDomain(99L, 1L))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.deleteDomain(99L, 1L))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.senders(99L)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(
            () ->
                controller.createSender(
                    99L, new CreateSenderRequest("1", "offers", "Offers", null)))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> controller.deleteSender(99L, 1L))
        .isInstanceOf(SecurityException.class);

    verify(authorizationService, times(10)).requirePermission(eq(7L), eq(99L), anyString());
    verifyNoInteractions(overviewService, domainService, senderService, rateLimiter);
  }
}
