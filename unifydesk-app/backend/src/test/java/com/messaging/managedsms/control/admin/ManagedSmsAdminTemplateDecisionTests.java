package com.messaging.managedsms.control.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsAdminDtos.DecisionRequest;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.VariableDefinition;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.msg91.Msg91ManagedSmsProviderAdapter;
import com.messaging.managedsms.control.provisioning.*;
import com.messaging.managedsms.control.repository.*;
import com.messaging.managedsms.control.service.*;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagedSmsAdminTemplateDecisionTests {

  private ManagedSmsTenantRepository tenantRepository;
  private ManagedSmsDltProfileRepository profileRepository;
  private ManagedSmsHeaderRepository headerRepository;
  private ManagedSmsTemplateRepository templateRepository;
  private ManagedSmsTemplateVersionRepository versionRepository;
  private ManagedSmsTemplateHeaderRepository templateHeaderRepository;
  private ManagedSmsProviderBindingRepository bindingRepository;
  private ManagedSmsAdminActionRepository auditRepository;
  private BroadcastSenderConnectionRepository connectionRepository;
  private ManagedSmsProvisioningService provisioningService;
  private ManagedSmsEligibilityService eligibilityService;
  private CurrentUserService currentUserService;
  private PlatformAdminAuthorizationService authorizationService;
  private ManagedSmsAdminService service;
  private ManagedSmsJsonSupport jsonSupport;
  private ManagedSmsTemplateVersion version;
  private ManagedSmsTemplate template;
  private ManagedSmsTenant tenant;
  private ManagedSmsHeader header;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(ManagedSmsTenantRepository.class);
    profileRepository = mock(ManagedSmsDltProfileRepository.class);
    headerRepository = mock(ManagedSmsHeaderRepository.class);
    templateRepository = mock(ManagedSmsTemplateRepository.class);
    versionRepository = mock(ManagedSmsTemplateVersionRepository.class);
    templateHeaderRepository = mock(ManagedSmsTemplateHeaderRepository.class);
    bindingRepository = mock(ManagedSmsProviderBindingRepository.class);
    auditRepository = mock(ManagedSmsAdminActionRepository.class);
    connectionRepository = mock(BroadcastSenderConnectionRepository.class);
    provisioningService = mock(ManagedSmsProvisioningService.class);
    eligibilityService = mock(ManagedSmsEligibilityService.class);
    currentUserService = mock(CurrentUserService.class);
    authorizationService = mock(PlatformAdminAuthorizationService.class);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    jsonSupport = new ManagedSmsJsonSupport(objectMapper);
    ManagedSmsCtaPolicyService ctaPolicyService =
        new ManagedSmsCtaPolicyService(objectMapper, jsonSupport);
    ManagedSmsProperties properties = new ManagedSmsProperties();
    Msg91ManagedSmsProviderAdapter providerAdapter =
        new Msg91ManagedSmsProviderAdapter(properties);

    service =
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
            providerAdapter,
            ctaPolicyService,
            eligibilityService,
            properties,
            currentUserService,
            authorizationService,
            objectMapper);

    when(currentUserService.currentUserId()).thenReturn(7L);

    template = new ManagedSmsTemplate();
    template.setId(10L);
    template.setCompanyId(1L);
    template.setStatus(TemplateStatus.DRAFT);

    version = new ManagedSmsTemplateVersion();
    version.setId(11L);
    version.setCompanyId(1L);
    version.setTemplateId(10L);
    version.setDltStatus(DltTemplateStatus.PENDING_DLT_APPROVAL);
    version.setStatus(TemplateVersionStatus.PENDING_DLT_APPROVAL);
    version.setVariablesSchemaJson(
        jsonSupport.variables(
            List.of(new VariableDefinition("name", VariableType.ALPHANUMERIC, 50))));

    tenant = new ManagedSmsTenant();
    tenant.setId(20L);
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.PROVIDER_SYNCING);

    header = new ManagedSmsHeader();
    header.setId(30L);
    header.setCompanyId(1L);
    header.setStatus(HeaderStatus.ACTIVE);
    header.setProviderMappingStatus(ProviderMappingStatus.READY);
    header.setStaleAfter(Instant.now().plusSeconds(3600));

    ManagedSmsTemplateHeader link = new ManagedSmsTemplateHeader();
    link.setId(new ManagedSmsTemplateHeaderId(11L, 30L));
    link.setCompanyId(1L);

    when(versionRepository.findById(11L)).thenReturn(Optional.of(version));
    when(templateRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(template));
    when(tenantRepository.findByCompanyId(1L)).thenReturn(Optional.of(tenant));
    when(templateHeaderRepository.findByCompanyIdAndIdTemplateVersionId(1L, 11L))
        .thenReturn(List.of(link));
    when(headerRepository.findByIdAndCompanyId(30L, 1L)).thenReturn(Optional.of(header));
    when(bindingRepository.findByCompanyIdAndTemplateVersionIdAndHeaderIdAndProvider(
            eq(1L), eq(11L), eq(30L), any()))
        .thenReturn(Optional.empty());
    when(bindingRepository.save(any(ManagedSmsProviderBinding.class)))
        .thenAnswer(
            invocation -> {
              ManagedSmsProviderBinding binding = invocation.getArgument(0);
              if (binding.getId() == null) binding.setId(40L);
              return binding;
            });
    when(versionRepository.saveAndFlush(any(ManagedSmsTemplateVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(versionRepository.save(any(ManagedSmsTemplateVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void providerTemplateRefAloneDoesNotActivateBindingOrTemplate() {
    DecisionRequest request =
        new DecisionRequest(
            AdminDecision.APPROVE,
            "DLT approved",
            0,
            null,
            null,
            "template-ref",
            null,
            null,
            null);

    var response = service.decideTemplate(11L, request);

    assertThat(response.state()).isEqualTo(TemplateVersionStatus.APPROVED_DLT.name());
    assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.APPROVED_DLT);
    assertThat(template.getStatus()).isEqualTo(TemplateStatus.DRAFT);
    verify(provisioningService)
        .enqueueOrReset(
            eq(1L),
            eq(20L),
            eq(ProvisioningResourceType.TEMPLATE_BINDING),
            eq(40L),
            eq(ProvisioningOperationType.ENSURE_TEMPLATE_BINDING),
            anyString());
  }

  @Test
  void flowRefWithConfirmedHeaderMappingCanActivateTemplate() {
    when(versionRepository.findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
            10L, 1L, TemplateVersionStatus.ACTIVE))
        .thenReturn(Optional.empty());

    DecisionRequest request =
        new DecisionRequest(
            AdminDecision.APPROVE,
            "DLT approved",
            0,
            null,
            null,
            null,
            "flow-ref",
            null,
            null);

    var response = service.decideTemplate(11L, request);

    assertThat(response.state()).isEqualTo(TemplateVersionStatus.ACTIVE.name());
    assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.ACTIVE);
    assertThat(template.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
    verify(provisioningService, never()).enqueueOrReset(any(), any(), any(), any(), any(), any());
  }

  @Test
  void missingAuthoritativeCtaRuleBlocksActivation() {
    version.setVariablesSchemaJson(
        jsonSupport.variables(
            List.of(new VariableDefinition("tracking_url", VariableType.URL, 300))));

    DecisionRequest request =
        new DecisionRequest(
            AdminDecision.APPROVE,
            "DLT approved",
            0,
            null,
            null,
            null,
            "flow-ref",
            null,
            null);

    assertThatThrownBy(() -> service.decideTemplate(11L, request))
        .isInstanceOf(com.messaging.common.exception.BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
    verifyNoInteractions(bindingRepository);
  }
}
