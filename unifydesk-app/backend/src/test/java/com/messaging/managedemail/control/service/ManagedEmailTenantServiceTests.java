package com.messaging.managedemail.control.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningOperationType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningResourceType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ManagedEmailTenantServiceTests {

  @Mock private ManagedEmailTenantRepository tenantRepository;
  @Mock private ManagedEmailProvisioningService provisioningService;
  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void queuesProviderRefreshForSendableTenantState() {
    ManagedEmailTenant tenant = new ManagedEmailTenant();
    tenant.setId(33L);
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.ACTIVE);
    when(tenantRepository.findAll()).thenReturn(List.of(tenant));

    ManagedEmailTenantService service =
        new ManagedEmailTenantService(
            tenantRepository,
            provisioningService,
            new ManagedEmailProperties(),
            jdbcTemplate);

    service.queueProviderRefreshes();

    verify(provisioningService)
        .enqueueOrReset(
            1L,
            ProvisioningResourceType.TENANT,
            33L,
            ProvisioningOperationType.ENSURE_TENANT,
            "tenant:ensure:1");
  }
}
