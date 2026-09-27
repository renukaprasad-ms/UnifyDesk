package com.messaging.managedsms.control.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsAdminDtos.DecisionRequest;
import com.messaging.managedsms.control.entity.ManagedSmsDltProfile;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.AdminDecision;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import com.messaging.managedsms.control.service.ManagedSmsEligibilityService;
import com.messaging.security.service.CurrentUserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsAdminServiceAuthorizationTests {

  @Test
  void adminDecisionAuthorizesCompanyBeforeMutationOrAudit() {
    ManagedSmsTenantRepository tenantRepository = mock(ManagedSmsTenantRepository.class);
    ManagedSmsDltProfileRepository profileRepository = mock(ManagedSmsDltProfileRepository.class);
    ManagedSmsHeaderRepository headerRepository = mock(ManagedSmsHeaderRepository.class);
    ManagedSmsTemplateRepository templateRepository = mock(ManagedSmsTemplateRepository.class);
    ManagedSmsTemplateVersionRepository versionRepository =
        mock(ManagedSmsTemplateVersionRepository.class);
    ManagedSmsTemplateHeaderRepository templateHeaderRepository =
        mock(ManagedSmsTemplateHeaderRepository.class);
    ManagedSmsProviderBindingRepository bindingRepository =
        mock(ManagedSmsProviderBindingRepository.class);
    ManagedSmsAdminActionRepository auditRepository = mock(ManagedSmsAdminActionRepository.class);
    BroadcastSenderConnectionRepository connectionRepository =
        mock(BroadcastSenderConnectionRepository.class);
    ManagedSmsProvisioningService provisioningService = mock(ManagedSmsProvisioningService.class);
    ManagedSmsEligibilityService eligibilityService = mock(ManagedSmsEligibilityService.class);
    CurrentUserService currentUserService = mock(CurrentUserService.class);
    PlatformAdminAuthorizationService authorizationService =
        mock(PlatformAdminAuthorizationService.class);

    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setId(10L);
    profile.setCompanyId(99L);
    profile.setTenantId(20L);
    when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
    when(currentUserService.currentUserId()).thenReturn(7L);
    doThrow(new SecurityException("denied"))
        .when(authorizationService)
        .requirePermission(7L, PlatformPermission.COMPANY_UPDATE);

    ManagedSmsAdminService service =
        new ManagedSmsAdminService(
            tenantRepository,
            profileRepository,
            headerRepository,
            templateRepository,
            versionRepository,
            templateHeaderRepository,
            bindingRepository,
            auditRepository,
            connectionRepository,
            provisioningService,
            eligibilityService,
            new ManagedSmsProperties(),
            currentUserService,
            authorizationService,
            new ObjectMapper());

    DecisionRequest request =
        new DecisionRequest(AdminDecision.APPROVE, "verified", 0, null, null, null, null, null);

    assertThatThrownBy(() -> service.decideProfile(10L, request))
        .isInstanceOf(SecurityException.class);
    verifyNoInteractions(auditRepository, tenantRepository);
  }
}
