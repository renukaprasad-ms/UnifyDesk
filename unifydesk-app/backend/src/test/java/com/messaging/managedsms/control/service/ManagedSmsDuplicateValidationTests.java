package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.common.exception.ConflictException;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.DltProfileRequest;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.HeaderRequest;
import com.messaging.managedsms.control.entity.ManagedSmsDltProfile;
import com.messaging.managedsms.control.entity.ManagedSmsTenant;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsDuplicateValidationTests {

  @Test
  void rejectsDuplicatePeId() {
    ManagedSmsTenantService tenantService = mock(ManagedSmsTenantService.class);
    ManagedSmsTenantRepository tenantRepository = mock(ManagedSmsTenantRepository.class);
    ManagedSmsDltProfileRepository profileRepository = mock(ManagedSmsDltProfileRepository.class);
    ManagedSmsProvisioningService provisioningService = mock(ManagedSmsProvisioningService.class);
    ManagedSmsJsonSupport jsonSupport = mock(ManagedSmsJsonSupport.class);
    ManagedSmsEligibilityService eligibilityService = mock(ManagedSmsEligibilityService.class);

    ManagedSmsTenant tenant = new ManagedSmsTenant();
    tenant.setId(2L);
    tenant.setCompanyId(1L);
    when(profileRepository.findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.empty());
    when(tenantService.ensureTenant(1L, 7L)).thenReturn(tenant);
    when(profileRepository.existsByCompanyIdAndPeIdAndArchivedAtIsNull(1L, "PE123"))
        .thenReturn(true);

    ManagedSmsProfileService service =
        new ManagedSmsProfileService(
            tenantService,
            tenantRepository,
            profileRepository,
            provisioningService,
            jsonSupport,
            eligibilityService);

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    new DltProfileRequest("Acme", DltPlatform.AIRTEL, "pe123", "TM-1", null)))
        .isInstanceOf(ConflictException.class);
    verify(profileRepository, never()).save(any());
  }

  @Test
  void rejectsDuplicateHeader() {
    ManagedSmsProfileService profileService = mock(ManagedSmsProfileService.class);
    ManagedSmsHeaderRepository headerRepository = mock(ManagedSmsHeaderRepository.class);
    ManagedSmsTenantRepository tenantRepository = mock(ManagedSmsTenantRepository.class);
    ManagedSmsSenderBindingRepository senderBindingRepository = mock(ManagedSmsSenderBindingRepository.class);
    com.messaging.broadcast.repository.BroadcastSenderRepository senderRepository =
        mock(com.messaging.broadcast.repository.BroadcastSenderRepository.class);
    ManagedSmsProvisioningService provisioningService = mock(ManagedSmsProvisioningService.class);
    ManagedSmsEligibilityService eligibilityService = mock(ManagedSmsEligibilityService.class);

    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setId(10L);
    profile.setCompanyId(1L);
    profile.setStatus(DltProfileStatus.ACTIVE);
    profile.setTmChainStatus(TmChainStatus.ACTIVE);
    profile.setStaleAfter(Instant.now().plusSeconds(3600));
    when(profileService.require(1L)).thenReturn(profile);
    when(headerRepository.existsByCompanyIdAndHeaderIgnoreCaseAndArchivedAtIsNull(1L, "ACME01"))
        .thenReturn(true);

    ManagedSmsHeaderService service =
        new ManagedSmsHeaderService(
            profileService,
            headerRepository,
            tenantRepository,
            senderBindingRepository,
            senderRepository,
            provisioningService,
            eligibilityService);

    assertThatThrownBy(
            () -> service.create(1L, new HeaderRequest("acme01", MessageCategory.SERVICE)))
        .isInstanceOf(ConflictException.class);
    verify(headerRepository, never()).save(any());
  }
}
