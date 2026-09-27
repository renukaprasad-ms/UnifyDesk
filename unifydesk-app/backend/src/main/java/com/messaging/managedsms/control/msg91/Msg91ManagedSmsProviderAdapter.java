package com.messaging.managedsms.control.msg91;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Msg91ManagedSmsProviderAdapter implements ManagedSmsProviderAdapter {

  private final ManagedSmsProperties properties;

  @Override
  public ProviderResult execute(ProviderRequest request) {
    if (!properties.isEnabled()) {
      return ProviderResult.actionRequired(
          "MANAGED_SMS_DISABLED", "Managed SMS is not enabled for provider provisioning.");
    }
    if (!properties.hasMasterAuthKey()) {
      return ProviderResult.actionRequired(
          "MSG91_MASTER_CREDENTIAL_REQUIRED",
          "Managed SMS provider provisioning requires platform configuration.");
    }

    /*
     * MSG91 documents B2B/subaccount and template APIs, but the exact production-contract
     * request/response shape for India PE/Header mapping is a frozen real-account validation
     * item. Never manufacture provider identifiers or READY state from local input.
     */
    return ProviderResult.actionRequired(
        "MSG91_MANUAL_REVIEW_REQUIRED",
        "Provider mapping requires UnifyDesk review before it can be activated.");
  }

  @Override
  public BindingReadiness bindingReadiness(BindingContext context) {
    if (context == null
        || context.binding() == null
        || context.tenant() == null
        || context.templateVersion() == null
        || context.header() == null) {
      return notReady("MANAGED_SMS_TEMPLATE_NOT_READY", "Provider binding is incomplete.");
    }

    ManagedSmsProviderBinding binding = context.binding();
    ManagedSmsTenant tenant = context.tenant();
    ManagedSmsTemplateVersion version = context.templateVersion();
    ManagedSmsHeader header = context.header();

    if (binding.getProvider() != BroadcastSenderProvider.MSG91_SMS
        || binding.getStatus() == ProviderBindingStatus.SUSPENDED
        || binding.getStatus() == ProviderBindingStatus.ARCHIVED) {
      return notReady("MANAGED_SMS_TEMPLATE_NOT_READY", "Provider binding is invalid.");
    }
    if (!Objects.equals(binding.getCompanyId(), tenant.getCompanyId())
        || !Objects.equals(binding.getCompanyId(), version.getCompanyId())
        || !Objects.equals(binding.getCompanyId(), header.getCompanyId())
        || !Objects.equals(binding.getTenantId(), tenant.getId())
        || !Objects.equals(binding.getTemplateVersionId(), version.getId())
        || !Objects.equals(binding.getHeaderId(), header.getId())) {
      return notReady("MANAGED_SMS_TEMPLATE_NOT_READY", "Provider binding ownership is invalid.");
    }
    if (tenant.getStatus() == TenantStatus.SUSPENDED
        || tenant.getStatus() == TenantStatus.ERROR
        || tenant.getStatus() == TenantStatus.ARCHIVED) {
      return notReady("MANAGED_SMS_SUSPENDED", "Managed SMS tenant is not eligible.");
    }
    if (header.getStatus() != HeaderStatus.ACTIVE
        || header.getProviderMappingStatus() != ProviderMappingStatus.READY
        || !fresh(header.getStaleAfter())) {
      return notReady("MANAGED_SMS_HEADER_NOT_READY", "Header provider mapping is not ready.");
    }
    if (version.getDltStatus() != DltTemplateStatus.APPROVED_DLT
        || version.getStatus() == TemplateVersionStatus.REJECTED
        || version.getStatus() == TemplateVersionStatus.STALE
        || version.getStatus() == TemplateVersionStatus.SUSPENDED
        || version.getStatus() == TemplateVersionStatus.ARCHIVED
        || version.getStatus() == TemplateVersionStatus.SUPERSEDED
        || !fresh(version.getStaleAfter())) {
      return notReady("MANAGED_SMS_TEMPLATE_NOT_READY", "Template approval is not current.");
    }
    if (binding.getProviderFlowRef() == null || binding.getProviderFlowRef().isBlank()) {
      return notReady(
          "MANAGED_SMS_TEMPLATE_NOT_READY", "A runtime-usable MSG91 Flow binding is required.");
    }
    return BindingReadiness.readyBinding();
  }

  private BindingReadiness notReady(String code, String message) {
    return BindingReadiness.notReady(code, message);
  }

  private boolean fresh(Instant staleAfter) {
    return staleAfter != null && staleAfter.isAfter(Instant.now());
  }
}
