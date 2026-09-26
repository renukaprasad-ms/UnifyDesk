package com.messaging.managedemail.control.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.CustomerDomainRequest;
import com.messaging.managedemail.control.security.ManagedEmailControlPlaneRateLimiter;
import com.messaging.managedemail.control.security.ManagedEmailControlPlaneRateLimiter.Operation;
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
class ManagedEmailControllerRateLimitTests {

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
  }

  @Test
  void deniedDomainCreateStopsBeforeProvisioningService() {
    doThrow(new TooManyRequestsException("limited"))
        .when(rateLimiter)
        .requireAllowed(Operation.CUSTOMER_DOMAIN_CREATE, 1L, 7L);

    assertThatThrownBy(
            () ->
                controller.createCustomerDomain(
                    1L, new CustomerDomainRequest("example.com", "request-key")))
        .isInstanceOf(TooManyRequestsException.class);

    verifyNoInteractions(domainService);
  }
}
