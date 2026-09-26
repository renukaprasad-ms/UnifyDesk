package com.messaging.managedemail.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.ConflictException;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.CustomerDomainRequest;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.DomainResponse;
import com.messaging.managedemail.control.entity.ManagedEmailDomain;
import com.messaging.managedemail.control.entity.ManagedEmailProvisioningOperation;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainType;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.ManagedEmailDomainRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ManagedEmailDomainServiceTests {

  @Mock private ManagedEmailDomainRepository domainRepository;
  @Mock private ManagedEmailTenantService tenantService;
  @Mock private ManagedEmailEntitlementService entitlementService;
  @Mock private ManagedEmailProvisioningService provisioningService;
  @Mock private ManagedEmailResponseMapper mapper;
  @Mock private ManagedEmailSenderLifecycleService senderLifecycleService;
  @Mock private CompanyRepository companyRepository;
  @Mock private JdbcTemplate jdbcTemplate;

  private ManagedEmailDomainNameService domainNameService;
  private ManagedEmailDomainService service;

  @BeforeEach
  void setUp() {
    domainNameService = new ManagedEmailDomainNameService();
    service =
        new ManagedEmailDomainService(
            domainRepository,
            tenantService,
            entitlementService,
            provisioningService,
            mapper,
            domainNameService,
            senderLifecycleService,
            companyRepository,
            new ManagedEmailProperties(),
            jdbcTemplate);
  }

  @Test
  void sameIdempotencyKeyReplaysExistingCompanyScopedDomain() {
    ManagedEmailProvisioningOperation operation = new ManagedEmailProvisioningOperation();
    operation.setResourceId(22L);
    ManagedEmailDomain domain = domain(1L);
    DomainResponse response =
        new DomainResponse(
            "22",
            DomainType.CUSTOMER_DOMAIN,
            "example.com",
            DomainStatus.PROVISIONING,
            null,
            null,
            null,
            null,
            java.util.List.of(),
            null,
            null);

    when(provisioningService.requestKey("customer-domain", 1L, "same-key"))
        .thenReturn("request-key");
    when(provisioningService.find("request-key")).thenReturn(Optional.of(operation));
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(domain));
    when(mapper.domain(domain)).thenReturn(response);

    var result =
        service.createCustomer(
            1L, 7L, new CustomerDomainRequest("example.com", "same-key"));

    assertThat(result.replay()).isTrue();
    assertThat(result.value()).isSameAs(response);
    verifyNoInteractions(tenantService);
  }

  @Test
  void serializesGlobalDomainClaimBeforeCheckingExistingOwner() {
    ManagedEmailDomain foreign = domain(2L);
    when(provisioningService.requestKey("customer-domain", 1L, "new-key"))
        .thenReturn("request-key");
    when(provisioningService.find("request-key")).thenReturn(Optional.empty());
    when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong())).thenReturn("");
    when(domainRepository.findLiveClaim(eq(DomainType.CUSTOMER_DOMAIN), eq("example.com"), any()))
        .thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                service.createCustomer(
                    1L, 7L, new CustomerDomainRequest("example.com", "new-key")))
        .isInstanceOf(ConflictException.class);

    InOrder order = inOrder(jdbcTemplate, domainRepository);
    order.verify(jdbcTemplate).queryForObject(anyString(), eq(String.class), anyLong());
    order.verify(domainRepository)
        .findLiveClaim(eq(DomainType.CUSTOMER_DOMAIN), eq("example.com"), any());
  }

  private ManagedEmailDomain domain(Long companyId) {
    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setId(22L);
    domain.setCompanyId(companyId);
    domain.setTenantId(33L);
    domain.setDomainType(DomainType.CUSTOMER_DOMAIN);
    domain.setNormalizedDomain("example.com");
    domain.setDisplayDomain("example.com");
    domain.setStatus(DomainStatus.PROVISIONING);
    return domain;
  }
}
