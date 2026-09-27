package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagedSmsTemplateServiceTests {

  @Mock private ManagedSmsTemplateRepository templateRepository;
  @Mock private ManagedSmsTemplateVersionRepository versionRepository;
  @Mock private ManagedSmsTemplateHeaderRepository templateHeaderRepository;
  @Mock private ManagedSmsProviderBindingRepository bindingRepository;
  @Mock private ManagedSmsHeaderService headerService;
  @Mock private ManagedSmsTenantRepository tenantRepository;
  @Mock private ManagedSmsProvisioningService provisioningService;
  @Mock private ManagedSmsJsonSupport jsonSupport;
  @Mock private ManagedSmsEligibilityService eligibilityService;
  @Mock private ManagedSmsCtaPolicyService ctaPolicyService;

  private ManagedSmsTemplateService service;

  @BeforeEach
  void setUp() {
    service =
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
  }

  @Test
  void rejectsForeignHeaderBeforeCreatingTemplateVersion() {
    ManagedSmsTemplate template = template();
    when(templateRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(template));
    when(versionRepository.existsByCompanyIdAndDltTemplateId(1L, "DLT-1")).thenReturn(false);
    when(headerService.require(1L, 99L)).thenThrow(new NotFoundException("Header not found"));

    var request =
        new TemplateVersionRequest(
            List.of("99"),
            "DLT-1",
            "Hello {#var#}",
            List.of(new VariableDefinition("name", VariableType.ALPHANUMERIC, 50)),
            null,
            null,
            null);

    assertThatThrownBy(() -> service.createVersion(1L, 10L, request))
        .isInstanceOf(NotFoundException.class);
    verify(versionRepository, never()).save(any());
  }

  @Test
  void rejectsDuplicateDltTemplateIdBeforeCreatingVersion() {
    when(templateRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(template()));
    when(versionRepository.existsByCompanyIdAndDltTemplateId(1L, "DLT-1")).thenReturn(true);

    var request =
        new TemplateVersionRequest(
            List.of("22"),
            "DLT-1",
            "Hello {#var#}",
            List.of(new VariableDefinition("name", VariableType.ALPHANUMERIC, 50)),
            null,
            null,
            null);

    assertThatThrownBy(() -> service.createVersion(1L, 10L, request))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(headerService);
  }

  @Test
  void activatedVersionCannotBeResubmittedAsMutableContent() {
    ManagedSmsTemplateVersion version = new ManagedSmsTemplateVersion();
    version.setId(11L);
    version.setCompanyId(1L);
    version.setTemplateId(10L);
    version.setStatus(TemplateVersionStatus.ACTIVE);
    when(templateRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(template()));
    when(versionRepository.findByIdAndCompanyId(11L, 1L)).thenReturn(Optional.of(version));

    assertThatThrownBy(() -> service.submit(1L, 10L, 11L))
        .isInstanceOf(com.messaging.common.exception.BadRequestException.class);
    verify(versionRepository, never()).save(any());
  }

  private ManagedSmsTemplate template() {
    ManagedSmsTemplate template = new ManagedSmsTemplate();
    template.setId(10L);
    template.setCompanyId(1L);
    template.setPurpose(MessageCategory.SERVICE);
    template.setStatus(TemplateStatus.DRAFT);
    return template;
  }
}
