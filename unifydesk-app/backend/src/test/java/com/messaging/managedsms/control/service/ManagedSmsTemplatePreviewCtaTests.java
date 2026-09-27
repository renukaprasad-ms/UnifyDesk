package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.PreviewRequest;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.VariableDefinition;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsTemplatePreviewCtaTests {

  @Test
  void previewDoesNotRenderUnapprovedCtaValue() throws Exception {
    ManagedSmsTemplateRepository templateRepository = mock(ManagedSmsTemplateRepository.class);
    ManagedSmsTemplateVersionRepository versionRepository =
        mock(ManagedSmsTemplateVersionRepository.class);
    ManagedSmsTemplateHeaderRepository templateHeaderRepository =
        mock(ManagedSmsTemplateHeaderRepository.class);
    ManagedSmsProviderBindingRepository bindingRepository =
        mock(ManagedSmsProviderBindingRepository.class);
    ManagedSmsHeaderService headerService = mock(ManagedSmsHeaderService.class);
    ManagedSmsTenantRepository tenantRepository = mock(ManagedSmsTenantRepository.class);
    ManagedSmsProvisioningService provisioningService = mock(ManagedSmsProvisioningService.class);
    ManagedSmsEligibilityService eligibilityService = mock(ManagedSmsEligibilityService.class);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    ManagedSmsJsonSupport jsonSupport = new ManagedSmsJsonSupport(objectMapper);
    ManagedSmsCtaPolicyService ctaPolicyService =
        new ManagedSmsCtaPolicyService(objectMapper, jsonSupport);

    ManagedSmsTemplate template = new ManagedSmsTemplate();
    template.setId(10L);
    template.setCompanyId(1L);
    template.setPurpose(MessageCategory.SERVICE);

    ManagedSmsHeader header = new ManagedSmsHeader();
    header.setId(20L);
    header.setCompanyId(1L);
    header.setStatus(HeaderStatus.ACTIVE);
    header.setProviderMappingStatus(ProviderMappingStatus.READY);

    List<VariableDefinition> schema =
        List.of(new VariableDefinition("tracking_url", VariableType.URL, 300));
    var policy = objectMapper.createObjectNode();
    policy.put("policyVersion", 2);
    var rule = policy.putArray("urlRules").addObject();
    rule.put("key", "tracking_url");
    rule.put("mode", "EXACT_VALUES");
    rule.putArray("allowedValues").add("https://example.com/orders/a");
    policy.putArray("callbackRules");

    ManagedSmsTemplateVersion version = new ManagedSmsTemplateVersion();
    version.setId(11L);
    version.setCompanyId(1L);
    version.setTemplateId(10L);
    version.setStatus(TemplateVersionStatus.ACTIVE);
    version.setBodyText("Track: {#var#}");
    version.setVariablesSchemaJson(jsonSupport.variables(schema));
    version.setCtaPolicyJson(
        ctaPolicyService.finalizeForActivation(version.getVariablesSchemaJson(), policy));

    ManagedSmsTemplateHeader link = new ManagedSmsTemplateHeader();
    link.setId(new ManagedSmsTemplateHeaderId(11L, 20L));
    link.setCompanyId(1L);

    when(templateRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(template));
    when(headerService.require(1L, 20L)).thenReturn(header);
    when(versionRepository.findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
            10L, 1L, TemplateVersionStatus.ACTIVE))
        .thenReturn(Optional.of(version));
    when(templateHeaderRepository.findByCompanyIdAndIdTemplateVersionId(1L, 11L))
        .thenReturn(List.of(link));

    ManagedSmsTemplateService service =
        new ManagedSmsTemplateService(
            templateRepository,
            versionRepository,
            templateHeaderRepository,
            bindingRepository,
            headerService,
            tenantRepository,
            provisioningService,
            jsonSupport,
            eligibilityService,
            ctaPolicyService);

    var response =
        service.preview(
            1L,
            10L,
            new PreviewRequest(
                "20", Map.of("tracking_url", "https://evil.example/orders/a")));

    assertThat(response.errors()).contains("tracking_url: CTA URL is not approved");
    assertThat(response.body()).isEqualTo("Track: ");
    assertThat(response.body()).doesNotContain("evil.example");
  }
}
