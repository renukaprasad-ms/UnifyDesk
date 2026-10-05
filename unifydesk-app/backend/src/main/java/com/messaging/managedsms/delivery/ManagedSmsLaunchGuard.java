package com.messaging.managedsms.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.exception.PlanEntitlementException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsLaunchGuard {

  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;
  private final ManagedSmsTemplateService templateService;
  private final ManagedSmsProviderSafetyService safetyService;
  private final ManagedSmsUsageService usageService;
  private final ManagedFinancialExposureGuard financialExposureGuard;
  private final ManagedSmsSegmentCalculator segmentCalculator;
  private final ObjectMapper objectMapper;

  public ManagedSmsRuntimeRepository.ManagedTemplateRuntime validateLaunch(
      Long companyId,
      BroadcastMessage message,
      BroadcastSender sender,
      BroadcastSenderConnection connection,
      long recipientCount) {
    if (connection.getManagementMode() != BroadcastSenderManagementMode.MESSAGING_MANAGED
        || sender.getProvider() != BroadcastSenderProvider.MSG91_SMS) {
      return null;
    }
    financialExposureGuard.assertAllowed(companyId);
    if (connection.getProvider() != BroadcastSenderProvider.MSG91_SMS
        || !properties.providerConfigured()) {
      throw new BadRequestException("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
    }
    if (properties.getCampaignMaxRecipients() <= 0
        || recipientCount > properties.getCampaignMaxRecipients()) {
      throw new BadRequestException("MANAGED_SMS_LIMIT_REACHED");
    }
    if (message.getSourceTemplateId() == null || message.getSourceTemplateVersion() == null) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }

    ManagedSmsRuntimeRepository.ManagedTemplateRuntime runtime =
        runtimeRepository
            .findTemplateForLaunch(
                companyId,
                sender.getId(),
                message.getSourceTemplateId(),
                message.getSourceTemplateVersion())
            .filter(ManagedSmsRuntimeRepository.ManagedTemplateRuntime::activeForCompose)
            .orElseThrow(() -> new BadRequestException("MANAGED_SMS_NOT_READY"));

    if (runtime.providerFlowRef() == null || runtime.providerFlowRef().isBlank()) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }
    if (!fresh(runtime.profileStaleAfter())
        || !fresh(runtime.headerStaleAfter())
        || !fresh(runtime.templateStaleAfter())
        || !freshByAge(runtime.providerBindingLastSyncAt())
        || !freshByAge(runtime.tenantLastProviderSyncAt())) {
      throw new BadRequestException("MANAGED_SMS_COMPLIANCE_STALE");
    }
    if (!safetyService.isAvailable(runtime.providerAccountKey())) {
      throw new BadRequestException("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
    }

    Map<String, String> variables = readVariables(message.getTemplateVariablesJson());
    ManagedSmsTemplateService.RenderedTemplate rendered =
        templateService.render(
            runtime.approvedBody(),
            runtime.bodyFingerprint(),
            runtime.variablesSchemaJson(),
            runtime.ctaPolicyJson(),
            variables);
    if (!rendered.body().equals(message.getBodyText())) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }

    int segments = segmentCalculator.segments(rendered.body());
    long requestedSegments;
    try {
      requestedSegments = Math.multiplyExact(recipientCount, segments);
    } catch (ArithmeticException exception) {
      throw new BadRequestException("MANAGED_SMS_LIMIT_REACHED");
    }
    if (!usageService.canReserve(companyId, requestedSegments)) {
      throw new PlanEntitlementException("MANAGED_SMS_LIMIT_REACHED");
    }
    if (isNewTenant(runtime.tenantCreatedAt())) {
      long cap = properties.getNewTenantDailyCap();
      if (cap <= 0 || requestedSegments > cap) {
        throw new BadRequestException("MANAGED_SMS_LIMIT_REACHED");
      }
    }
    return runtime;
  }

  private boolean fresh(Instant staleAfter) {
    return staleAfter != null && staleAfter.isAfter(Instant.now());
  }

  private boolean freshByAge(Instant checkedAt) {
    Duration maxAge = properties.getComplianceStaleAfter();
    return checkedAt != null
        && maxAge != null
        && !maxAge.isZero()
        && !maxAge.isNegative()
        && checkedAt.plus(maxAge).isAfter(Instant.now());
  }

  private boolean isNewTenant(Instant createdAt) {
    return createdAt == null || createdAt.plus(Duration.ofDays(7)).isAfter(Instant.now());
  }

  private Map<String, String> readVariables(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      Map<String, String> values =
          objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return values == null ? Map.of() : Map.copyOf(values);
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }
  }
}
