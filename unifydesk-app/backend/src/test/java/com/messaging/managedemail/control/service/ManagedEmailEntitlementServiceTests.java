package com.messaging.managedemail.control.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.service.EntitlementService;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.repository.ManagedEmailDomainRepository;
import com.messaging.managedemail.control.repository.ManagedEmailSenderBindingRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagedEmailEntitlementServiceTests {

  @Mock private EntitlementService entitlementService;
  @Mock private ManagedEmailDomainRepository domainRepository;
  @Mock private ManagedEmailSenderBindingRepository senderBindingRepository;
  @InjectMocks private ManagedEmailEntitlementService service;

  @Test
  void rejectedRequestsDoNotConsumeDomainCapacity() {
    Set<DomainStatus> terminal =
        Set.of(DomainStatus.DELETED, DomainStatus.RETIRED, DomainStatus.REJECTED);
    when(domainRepository.countByCompanyIdAndStatusNotIn(1L, terminal)).thenReturn(0L);

    service.requireDomainCapacity(1L);

    verify(entitlementService)
        .assertWithinLimit(1L, ManagedEmailEntitlementService.DOMAINS, 0L);
  }
}
