package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagedSmsStatusServiceTests {

  private ManagedSmsTenantRepository tenantRepository;
  private ManagedSmsDltProfileRepository profileRepository;
  private ManagedSmsHeaderRepository headerRepository;
  private ManagedSmsTemplateVersionRepository versionRepository;
  private ManagedSmsProviderBindingRepository bindingRepository;
  private ManagedSmsEligibilityService eligibilityService;
  private ManagedSmsProperties properties;
  private ManagedSmsStatusService service;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(ManagedSmsTenantRepository.class);
    profileRepository = mock(ManagedSmsDltProfileRepository.class);
    headerRepository = mock(ManagedSmsHeaderRepository.class);
    versionRepository = mock(ManagedSmsTemplateVersionRepository.class);
    bindingRepository = mock(ManagedSmsProviderBindingRepository.class);
    eligibilityService = mock(ManagedSmsEligibilityService.class);
    properties = new ManagedSmsProperties();
    properties.setEnabled(true);
    service =
        new ManagedSmsStatusService(
            profileRepository,
            headerRepository,
            versionRepository,
            bindingRepository,
            eligibilityService,
            properties);
  }

  @Test
  void suspendedTenantIsFailClosed() {
    ManagedSmsTenant tenant = new ManagedSmsTenant();
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.SUSPENDED);
    when(eligibilityService.recalculate(1L)).thenReturn(tenant);
    when(profileRepository.findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.empty());

    assertThat(service.get(1L).state()).isEqualTo(CustomerState.SUSPENDED);
    assertThat(service.get(1L).sendingEnabled()).isFalse();
  }

  @Test
  void rejectedProfileIsVisibleAsRejected() {
    ManagedSmsTenant tenant = new ManagedSmsTenant();
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.PROVIDER_SYNCING);
    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setId(2L);
    profile.setPeId("PE123456");
    profile.setStatus(DltProfileStatus.REJECTED);
    profile.setTmChainStatus(TmChainStatus.REJECTED);
    when(eligibilityService.recalculate(1L)).thenReturn(tenant);
    when(profileRepository.findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(profile));

    assertThat(service.get(1L).state()).isEqualTo(CustomerState.REJECTED);
  }

  @Test
  void staleComplianceRequiresActionAndCannotReportReady() {
    ManagedSmsTenant tenant = new ManagedSmsTenant();
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.PROVIDER_SYNCING);
    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setId(2L);
    profile.setPeId("PE123456");
    profile.setStatus(DltProfileStatus.ACTIVE);
    profile.setTmChainStatus(TmChainStatus.ACTIVE);
    profile.setStaleAfter(Instant.now().minusSeconds(1));
    when(eligibilityService.recalculate(1L)).thenReturn(tenant);
    when(eligibilityService.fresh(profile.getStaleAfter())).thenReturn(false);
    when(profileRepository.findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(profile));

    assertThat(service.get(1L).state()).isEqualTo(CustomerState.ACTION_REQUIRED);
    assertThat(service.get(1L).sendingEnabled()).isFalse();
  }
}
