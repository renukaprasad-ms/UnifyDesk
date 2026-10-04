package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.metering.ManagedSmsSegmentCalculator;
import com.messaging.managedsms.metering.ManagedSmsUsageService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsLaunchGuardRound2Tests {

  @Test
  void activeManagedSmsLaunchPassesFinancialBoundaryAndReservationCheck() {
    ManagedSmsRuntimeRepository runtimeRepository = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsProviderSafetyService safety = mock(ManagedSmsProviderSafetyService.class);
    ManagedSmsUsageService usage = mock(ManagedSmsUsageService.class);
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    ManagedSmsSegmentCalculator segments = mock(ManagedSmsSegmentCalculator.class);
    ManagedSmsTemplateService templateService = mock(ManagedSmsTemplateService.class);
    ManagedSmsRuntimeProperties properties = configuredProperties();

    BroadcastMessage message = new BroadcastMessage();
    message.setSourceTemplateId(43L);
    message.setSourceTemplateVersion(1L);
    message.setBodyText("Code 123");
    message.setTemplateVariablesJson("{\"code\":\"123\"}");

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    ManagedSmsRuntimeRepository.ManagedTemplateRuntime runtime = activeRuntime();
    when(runtimeRepository.findTemplateForLaunch(10L, 20L, 43L, 1L))
        .thenReturn(Optional.of(runtime));
    when(safety.isAvailable("client-a")).thenReturn(true);
    when(templateService.render(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(
            new ManagedSmsTemplateService.RenderedTemplate(
                "Code 123", Map.of("code", "123"), java.util.List.of()));
    when(segments.segments("Code 123")).thenReturn(1);
    when(usage.canReserve(10L, 1L)).thenReturn(true);

    ManagedSmsLaunchGuard guard =
        new ManagedSmsLaunchGuard(
            runtimeRepository,
            properties,
            templateService,
            safety,
            usage,
            exposure,
            segments,
            new ObjectMapper());

    org.assertj.core.api.Assertions.assertThat(
            guard.validateLaunch(10L, message, sender, connection, 1L))
        .isSameAs(runtime);
    verify(exposure).assertAllowed(10L);
    verify(usage).canReserve(10L, 1L);
  }

  @Test
  void blockedBillingStatusRejectsManagedSmsBeforeRuntimeOrReservationChecks() {
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsProviderSafetyService safety = mock(ManagedSmsProviderSafetyService.class);
    ManagedSmsUsageService usage = mock(ManagedSmsUsageService.class);
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    doThrow(
            new com.messaging.billing.exception.PlanEntitlementException(
                ManagedFinancialExposureGuard.BLOCKED))
        .when(exposure)
        .assertAllowed(10L);

    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");

    ManagedSmsLaunchGuard guard =
        new ManagedSmsLaunchGuard(
            runtime,
            properties,
            mock(ManagedSmsTemplateService.class),
            safety,
            usage,
            exposure,
            mock(ManagedSmsSegmentCalculator.class),
            new ObjectMapper());

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    BroadcastSender sender = new BroadcastSender();
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    assertThatThrownBy(
            () -> guard.validateLaunch(10L, new BroadcastMessage(), sender, connection, 1L))
        .isInstanceOf(com.messaging.billing.exception.PlanEntitlementException.class)
        .hasMessage(ManagedFinancialExposureGuard.BLOCKED);

    verifyNoInteractions(runtime, safety, usage);
  }

  @Test
  void customerOwnedSmsDoesNotUseManagedFinancialExposureGuard() {
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    ManagedSmsLaunchGuard guard =
        new ManagedSmsLaunchGuard(
            mock(ManagedSmsRuntimeRepository.class),
            new ManagedSmsRuntimeProperties(),
            mock(ManagedSmsTemplateService.class),
            mock(ManagedSmsProviderSafetyService.class),
            mock(ManagedSmsUsageService.class),
            exposure,
            mock(ManagedSmsSegmentCalculator.class),
            new ObjectMapper());

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    BroadcastSender sender = new BroadcastSender();
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    org.assertj.core.api.Assertions.assertThat(
            guard.validateLaunch(10L, new BroadcastMessage(), sender, connection, 1L))
        .isNull();
    verifyNoInteractions(exposure);
  }

  private ManagedSmsRuntimeProperties configuredProperties() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setCampaignMaxRecipients(100);
    properties.setNewTenantDailyCap(1000);
    return properties;
  }

  private ManagedSmsRuntimeRepository.ManagedTemplateRuntime activeRuntime() {
    Instant now = Instant.now();
    return new ManagedSmsRuntimeRepository.ManagedTemplateRuntime(
        10L,
        20L,
        30L,
        40L,
        now.minusSeconds(864000),
        "READY",
        true,
        now,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        42L,
        "ACMEIN",
        "SERVICE",
        "ACTIVE",
        "READY",
        now,
        now.plusSeconds(3600),
        43L,
        44L,
        1L,
        "dlt",
        "Code {#var#}",
        "fingerprint",
        "[{\"key\":\"code\",\"type\":\"NUMERIC\",\"maxLength\":10}]",
        "{}",
        "rules",
        "ACTIVE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        45L,
        "CHILD_PER_COMPANY",
        "client-a",
        "provider-template",
        "provider-flow",
        "ACTIVE",
        now,
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }

  @Test
  void absoluteCampaignCapBlocksBeforeRuntimeOrProviderWork() {
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsProviderSafetyService safety = mock(ManagedSmsProviderSafetyService.class);
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setCampaignMaxRecipients(100);

    ManagedSmsLaunchGuard guard =
        new ManagedSmsLaunchGuard(
            runtime,
            properties,
            mock(ManagedSmsTemplateService.class),
            safety,
            mock(ManagedSmsUsageService.class),
            mock(ManagedFinancialExposureGuard.class),
            mock(ManagedSmsSegmentCalculator.class),
            new ObjectMapper());

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);

    BroadcastSender sender = new BroadcastSender();
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    assertThatThrownBy(
            () ->
                guard.validateLaunch(
                    10L, new BroadcastMessage(), sender, connection, 101L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_LIMIT_REACHED");

    verifyNoInteractions(runtime, safety);
  }
}
