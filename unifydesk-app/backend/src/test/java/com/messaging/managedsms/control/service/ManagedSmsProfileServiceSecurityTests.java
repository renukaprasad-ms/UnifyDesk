package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.DltProfilePatchRequest;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedSmsProfileServiceSecurityTests {

  private ManagedSmsTenantService tenantService;
  private ManagedSmsTenantRepository tenantRepository;
  private ManagedSmsDltProfileRepository profileRepository;
  private ManagedSmsComplianceActionRepository complianceActionRepository;
  private ManagedSmsProvisioningService provisioningService;
  private ManagedSmsEligibilityService eligibilityService;
  private ManagedSmsProfileService service;

  @BeforeEach
  void setUp() {
    tenantService = mock(ManagedSmsTenantService.class);
    tenantRepository = mock(ManagedSmsTenantRepository.class);
    profileRepository = mock(ManagedSmsDltProfileRepository.class);
    complianceActionRepository = mock(ManagedSmsComplianceActionRepository.class);
    provisioningService = mock(ManagedSmsProvisioningService.class);
    eligibilityService = mock(ManagedSmsEligibilityService.class);
    ManagedSmsJsonSupport jsonSupport = new ManagedSmsJsonSupport(new ObjectMapper().findAndRegisterModules());

    when(profileRepository.saveAndFlush(any(ManagedSmsDltProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service =
        new ManagedSmsProfileService(
            tenantService,
            tenantRepository,
            profileRepository,
            complianceActionRepository,
            provisioningService,
            jsonSupport,
            eligibilityService);
  }

  @Test
  void activeLegalNameEditInvalidatesApprovalAndAudits() {
    assertReviewInvalidated(
        new DltProfilePatchRequest("Acme Renamed", null, null, null, null),
        profile -> assertThat(profile.getLegalName()).isEqualTo("Acme Renamed"),
        false);
  }

  @Test
  void activeDltPlatformEditInvalidatesApprovalAndAudits() {
    assertReviewInvalidated(
        new DltProfilePatchRequest(null, DltPlatform.JIO, null, null, null),
        profile -> assertThat(profile.getDltPlatform()).isEqualTo(DltPlatform.JIO),
        false);
  }

  @Test
  void activeEvidenceEditInvalidatesApprovalAndAudits() throws Exception {
    assertReviewInvalidated(
        new DltProfilePatchRequest(
            null, null, null, null, new ObjectMapper().readTree("{\"document\":\"new\"}")),
        profile -> assertThat(profile.getEvidenceJson()).contains("new"),
        false);
  }

  @Test
  void activeTmReferenceEditAlsoInvalidatesChainApproval() {
    assertReviewInvalidated(
        new DltProfilePatchRequest(null, null, null, "TM-NEW", null),
        profile -> assertThat(profile.getTmChainReference()).isEqualTo("TM-NEW"),
        true);
  }

  @Test
  void foreignCompanyProfileIdCannotBeMutated() {
    when(profileRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.patch(
                    1L,
                    10L,
                    7L,
                    new DltProfilePatchRequest("Other", null, null, null, null)))
        .isInstanceOf(com.messaging.common.exception.NotFoundException.class);

    verifyNoInteractions(complianceActionRepository, eligibilityService);
  }

  @Test
  void activePeIdCannotChangeInPlace() {
    ManagedSmsDltProfile profile = activeProfile();
    when(profileRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(profile));

    assertThatThrownBy(
            () ->
                service.patch(
                    1L,
                    10L,
                    7L,
                    new DltProfilePatchRequest(null, null, "PE999", null, null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("cannot be replaced");

    verify(profileRepository, never()).save(any());
    verify(profileRepository, never()).saveAndFlush(any());
    verifyNoInteractions(complianceActionRepository, eligibilityService);
  }

  private void assertReviewInvalidated(
      DltProfilePatchRequest request,
      Consumer<ManagedSmsDltProfile> changedAssertion,
      boolean chainMustBeInvalidated) {
    ManagedSmsDltProfile profile = activeProfile();
    when(profileRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(profile));

    service.patch(1L, 10L, 7L, request);

    assertThat(profile.getStatus()).isEqualTo(DltProfileStatus.WAITING_UNIFYDESK_REVIEW);
    assertThat(profile.getVerifiedAt()).isNull();
    assertThat(profile.getCheckedAt()).isNull();
    assertThat(profile.getStaleAfter()).isNull();
    if (chainMustBeInvalidated) {
      assertThat(profile.getTmChainStatus()).isEqualTo(TmChainStatus.WAITING_UNIFYDESK_REVIEW);
    } else {
      assertThat(profile.getTmChainStatus()).isEqualTo(TmChainStatus.ACTIVE);
    }
    changedAssertion.accept(profile);

    ArgumentCaptor<ManagedSmsComplianceAction> audit =
        ArgumentCaptor.forClass(ManagedSmsComplianceAction.class);
    verify(complianceActionRepository).save(audit.capture());
    assertThat(audit.getValue().getActorUserId()).isEqualTo(7L);
    assertThat(audit.getValue().getCompanyId()).isEqualTo(1L);
    assertThat(audit.getValue().getResourceId()).isEqualTo(10L);
    assertThat(audit.getValue().getBeforeStateJson()).contains("\"status\":\"ACTIVE\"");
    assertThat(audit.getValue().getAfterStateJson())
        .contains("\"status\":\"WAITING_UNIFYDESK_REVIEW\"");
    verify(eligibilityService).recalculate(1L);
  }

  private ManagedSmsDltProfile activeProfile() {
    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setId(10L);
    profile.setCompanyId(1L);
    profile.setTenantId(20L);
    profile.setLegalName("Acme Pvt Ltd");
    profile.setDltPlatform(DltPlatform.AIRTEL);
    profile.setPeId("PE123");
    profile.setStatus(DltProfileStatus.ACTIVE);
    profile.setTmChainStatus(TmChainStatus.ACTIVE);
    profile.setTmChainReference("TM-OLD");
    profile.setEvidenceJson("{\"document\":\"old\"}");
    profile.setRulesetVersion("INDIA_TCCCPR_2025");
    profile.setVerifiedAt(Instant.now().minusSeconds(60));
    profile.setCheckedAt(Instant.now().minusSeconds(60));
    profile.setStaleAfter(Instant.now().plusSeconds(3600));
    return profile;
  }
}
